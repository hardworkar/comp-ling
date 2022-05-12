import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    final static ArrayList<String> to_skip = new ArrayList<>(
            Arrays.asList(",", ".", "!", ":", "?", "(", ")", "\"", ";", "«", "»", ";", "/"));
    public static void main(String[] args) throws XMLStreamException, IOException {
        ArrayList<String> texts = readTextCorpus();
        ArrayList<ArrayList<String>> tokenized_texts = texts.stream().map(Main::tokenize).collect(Collectors.toCollection(ArrayList::new));

        /*
        System.out.println("Всего текстов: " + texts.size());
        System.out.println("Всего токенов: " + tokenized_texts.stream().map(x -> x.stream().filter(y -> !to_skip.contains(y)).count()).reduce(0L, Long::sum));
        */

        Map<String, ArrayList<Lemma>> form_to_lemmas = getDictionary();

        ArrayList<ArrayList<ArrayList<Lemma>>> lemmatized_texts = tokenized_texts.stream().map(x -> lemmatize_text(form_to_lemmas, x)).collect(Collectors.toCollection(ArrayList::new));

        // getNgrams(lemmatized_texts);

        ArrayList<Model> models = readModels();
        Map<String, String> semantics = readSemantics();

        ArrayList<ArrayList<Sentence>> sentences = lemmatized_texts.stream().map(Main::get_sentences).collect(Collectors.toCollection(ArrayList::new));
        Map<Model, ArrayList<Sentence>> found_models = new HashMap<>();
        for(var model : models){
            found_models.put(model, new ArrayList<>());
        }
        int matched_sentences = 0;
        for(var text : sentences){
            for(Sentence sentence : text) {
                boolean matched = false;
                for (var model : models) {
                    if (model.accept(sentence, semantics)) {
                        found_models.get(model).add(sentence);
                        matched = true;
                    }
                }
                if(matched)
                    matched_sentences++;
            }
        }
        FileWriter fileWriter = new FileWriter("matched_sentences.txt");
        PrintWriter printWriter = new PrintWriter(fileWriter);
        for(var found_model : found_models.entrySet()){
           printWriter.println(found_model.getKey() + ": " + found_model.getValue().size());
           for(var s : found_model.getValue()){
               printWriter.println(">>= " + s);
           }
        }
        printWriter.println("Подходящие предложения: " + matched_sentences);
        printWriter.println("Полнота: " + 100 * (double) matched_sentences / sentences.stream().map(ArrayList::size).reduce(Integer::sum).orElse(0) + "%");
        printWriter.close();
    }


    private static ArrayList<Sentence> get_sentences(ArrayList<ArrayList<Lemma>> text){
        ArrayList<Sentence> sentences = new ArrayList<>();
        Sentence sentence = new Sentence();
        for(ArrayList<Lemma> wordInText : text){
            // wordInText contains all possible lemmas, we'll just pick the first one
            Lemma actual = wordInText.get(0);
            sentence.add(actual);
            if(actual.init.t.equals(".") || actual.init.t.equals("!") || actual.init.t.equals("?")){
               sentences.add(sentence);
               sentence = new Sentence();
            }
        }
        return sentences;
    }

    private static ArrayList<Model> readModels() {
        ArrayList<Model> models = new ArrayList<>();
        Scanner sc2 = null;
        try {
            sc2 = new Scanner(new File("models.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while (sc2.hasNextLine()) {
            Scanner s2 = new Scanner(sc2.nextLine());
            Model model = new Model();
            while (s2.hasNext()) {
                String s = s2.next();
                model.add(s);
            }
            models.add(model);
        }
        return models;
    }

    // returns mapping:
    // from lemma.id to its 'role'
    private static Map<String, String> readSemantics() {
        Map<String, String> semantics = new HashMap<>();
        Scanner sc2 = null;
        try {
            sc2 = new Scanner(new File("semantics.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while (sc2.hasNextLine()) {
            Scanner s2 = new Scanner(sc2.nextLine());
            String lemmaId = s2.next();
            String role = s2.next();
            semantics.put(lemmaId, role);
        }
        return semantics;
    }

    private static void getNgrams(ArrayList<ArrayList<ArrayList<Lemma>>> lemmatized_texts) throws IOException {
        Map<Integer, Integer> number_of_ngrams = new HashMap<>();
        Files.deleteIfExists(Paths.get("ngrams.txt"));
        for(int ngram_length = 2 ; ngram_length < 30 ; ngram_length ++) {
            int RELATIVE_FREQ_THRESHOLD = 1;
            int NGRAM_ABS_FREQ_THRESHOLD = 3;
            Map<ArrayList<ArrayList<Lemma>>, NgramContext> ngram_stats = countContexts(ngram_length, lemmatized_texts);
            var sorted_ngrams = sort_context(ngram_stats);

            var filtered_ngrams = sorted_ngrams.stream().filter(ngram -> {
                var sorted_left = new LinkedList<>(ngram.getValue().left_expands.entrySet());
                sorted_left.sort(Comparator.comparingInt(Map.Entry::getValue));
                Collections.reverse(sorted_left);

                var sorted_right = new LinkedList<>(ngram.getValue().right_expands.entrySet());
                sorted_right.sort(Comparator.comparingInt(Map.Entry::getValue));
                Collections.reverse(sorted_right);

                Integer Fx = ngram.getValue().abs_freq;
                Integer Fax = sorted_left.size() > 0 ? sorted_left.get(0).getValue() : 0;
                Integer Fxb = sorted_right.size() > 0 ? sorted_right.get(0).getValue() : 0;

                return (Fax / Fx < RELATIVE_FREQ_THRESHOLD && Fxb / Fx < RELATIVE_FREQ_THRESHOLD) && ngram.getValue().abs_freq >= NGRAM_ABS_FREQ_THRESHOLD;
            }).toList();
            if(filtered_ngrams.size() == 0){
                break;
            }
            number_of_ngrams.put(ngram_length, filtered_ngrams.size());
            print_ngrams(prepare_ngrams(filtered_ngrams));
        }
        try(FileWriter fileWriter = new FileWriter("ngrams.txt", true)) {
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println("Всего устойчивых n-грамм: " + number_of_ngrams.values().stream().reduce(0, Integer::sum));
            for(var nn : number_of_ngrams.entrySet()){
               printWriter.printf("%d-грамм: %d\n", nn.getKey(), nn.getValue());
            }
        } catch (IOException e) {
            System.out.println("error opening output file: " + e);
        }
    }

    private static Map<String, ArrayList<Lemma>> getDictionary() throws XMLStreamException {
        Dict dict = null;
        try {
            dict = parse_dict("../../dict/annot.opcorpora.xml/dict.opcorpora.xml");
        } catch (IOException e) {
            System.out.println("error opening dictionary file: " + e);
        }
        assert dict != null;
        return create_form_to_lemmas(dict.lemmata);
    }

    private static ArrayList<String> readTextCorpus() {
        ArrayList<String> texts = null;
        try {
            texts = readCorpus("../../corp/corp/out.txt");
        } catch (IOException e) {
            System.out.println("error opening input file: " + e);
        }
        assert texts != null;
        return texts;
    }
    private static Map<ArrayList<ArrayList<Lemma>>, NgramContext> countContexts(
            int ngram_length,
            ArrayList<ArrayList<ArrayList<Lemma>>> lemmatized_texts) {
        Map<ArrayList<ArrayList<Lemma>>, NgramContext> ngram_stats = new HashMap<>();
        for (int txt_idx = 0; txt_idx < lemmatized_texts.size(); txt_idx++) {
            var ltext = lemmatized_texts.get(txt_idx);
            for (int i = 0; i < ltext.size() - ngram_length; i++) {
                int c = i;
                ArrayList<ArrayList<Lemma>> ngram = new ArrayList<>();
                while (c < ltext.size() && (c - i) < ngram_length) {
                    ngram.add(ltext.get(c));
                    c++;
                }
                ngram_stats.putIfAbsent(ngram, new NgramContext());
                ngram_stats.get(ngram).abs_freq++;
                ngram_stats.get(ngram).texts_mentioned.add(txt_idx);

                int ctx_idx;
                ctx_idx = i - 1;
                ArrayList<Lemma> left_context = new ArrayList<>();
                while (ctx_idx >= 0 && (i - ctx_idx <= 1)) {
                    left_context.add(ltext.get(ctx_idx).get(0));
                    add_context(ngram_stats.get(ngram).left_expands, i - ctx_idx, true, left_context);
                    ctx_idx--;
                }
                ArrayList<Lemma> right_context = new ArrayList<>();
                ctx_idx = i + ngram_length;
                while (ctx_idx < ltext.size() && (ctx_idx - (i + ngram_length) <= 1)) {
                    right_context.add(ltext.get(ctx_idx).get(0));
                    add_context(ngram_stats.get(ngram).right_expands, ctx_idx - i, false, right_context);
                    ctx_idx++;
                }

            }
        }
        return ngram_stats;
    }

    record Ngram_Record(ArrayList<ArrayList<Lemma>> lemmas, int abs_freq, int text_freq, float left,
                        float right) {
    }
    private static List<Ngram_Record> prepare_ngrams(List<Map.Entry<ArrayList<ArrayList<Lemma>>, NgramContext>> sorted) {
        List<Ngram_Record> out = new ArrayList<>();
        for (var ngram : sorted) {
            var sorted_left = new LinkedList<>(ngram.getValue().left_expands.entrySet());
            sorted_left.sort(Comparator.comparingInt(Map.Entry::getValue));
            Collections.reverse(sorted_left);

            var sorted_right = new LinkedList<>(ngram.getValue().right_expands.entrySet());
            sorted_right.sort(Comparator.comparingInt(Map.Entry::getValue));
            Collections.reverse(sorted_right);

            Integer Fx = ngram.getValue().abs_freq;
            int Fax = sorted_left.size() > 0 ? sorted_left.get(0).getValue() : 0;
            int Fxb = sorted_right.size() > 0 ? sorted_right.get(0).getValue() : 0;

            out.add(new Ngram_Record(
                    ngram.getKey(),
                    ngram.getValue().abs_freq,
                    ngram.getValue().texts_mentioned.size(),
                    Fax / (float) Fx,
                    Fxb / (float) Fx
            ));
        }
        out.sort((o1, o2) -> Float.compare(o1.left + o1.right, o2.left + o2.right));
        return out;
    }
    private static void print_ngrams(List<Ngram_Record> sorted) {
        try(FileWriter fileWriter = new FileWriter("ngrams.txt", true)) {
            PrintWriter printWriter = new PrintWriter(fileWriter);
            for(var ngram : sorted) {
                ngram.lemmas.forEach(x -> printWriter.print(x.get(0).init.t + " "));
                printWriter.printf("[%d] ", ngram.abs_freq);
                printWriter.printf("[%d] ", ngram.text_freq);
                printWriter.printf("[%f, %f]", ngram.left, ngram.right);
                printWriter.println();
            }
        } catch (IOException e) {
            System.out.println("error opening output file: " + e);
        }
    }

    private static void add_context(Map<ArrayList<Lemma>, Integer> context_stats, int dist, boolean reverse, ArrayList<Lemma> context) {
        if(dist > 0){
            var copy = new ArrayList<>(context);
            if(reverse) {
                Collections.reverse(copy);
            }
            context_stats.putIfAbsent(copy, 0);
            context_stats.put(copy, context_stats.get(copy) + 1);
        }
    }

    private static <T> List<Map.Entry<T, NgramContext>> sort_context(Map<T, NgramContext> context_stats){
        List<Map.Entry<T, NgramContext>> context_list = new LinkedList<>(context_stats.entrySet());
        context_list.sort(Comparator.comparingInt(o -> o.getValue().abs_freq));
        Collections.reverse(context_list);
        return context_list;
    }
    private static ArrayList<ArrayList<Lemma>> lemmatize_text(Map<String, ArrayList<Lemma>> form_to_lemmas, ArrayList<String> tokenized_text) {
        ArrayList<ArrayList<Lemma>> lemmatized_text = new ArrayList<>();
        for (String word : tokenized_text) {
            if(to_skip.contains(word)) {
                lemmatized_text.add(new ArrayList<>(List.of(new Lemma(word))));
            }
            else if (!form_to_lemmas.containsKey(word)){
                // dummy bummy mishki gammi
                lemmatized_text.add(new ArrayList<>(List.of(new Lemma(word))));
            }
            else {
                lemmatized_text.add(form_to_lemmas.get(word));
            }
        }
        return lemmatized_text;
    }

    private static Map<String, ArrayList<Lemma>> create_form_to_lemmas(ArrayList<Lemma> lemmata) {
        Map<String, ArrayList<Lemma>> form_to_lemmas = new HashMap<>();
        for(var lemma : lemmata){
            Set<String> all_forms = new HashSet<>();
            all_forms.add(lemma.init.t);
            for(var form : lemma.forms){
                all_forms.add(form.t);
            }
            for(var form : all_forms.stream().toList()){
                if(form_to_lemmas.containsKey(form)){
                    form_to_lemmas.get(form).add(lemma);
                }
                else{
                    form_to_lemmas.put(form, new ArrayList<>(List.of(lemma)));
                }
            }
        }
        return form_to_lemmas;
    }

    private static ArrayList<String> tokenize(String text) {
        String modified = text;
        for(String del : to_skip) {
            modified = modified.replace(del, "#" + del + "#");
        }
        var tokens = new ArrayList<>(Arrays.asList(modified.split("\\s+|#")));
        return tokens.stream()
                .map(String::trim)
                .filter(x -> x.length() > 0)
                .filter(x -> !x.matches(".*[a-zA-Z]+.*"))
                .map(x -> x.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(ArrayList::new));
    }

    public static ArrayList<String> readCorpus(String fileName) throws IOException {
        Path fName = Path.of(fileName);
        String actual = Files.readString(fName);
        return new ArrayList<>(Arrays.stream(actual.split("#")).toList());
    }

    public static Dict parse_dict(String path_to_dict) throws IOException, XMLStreamException {
        XMLInputFactory streamFactory = XMLInputFactory.newInstance();
        XMLStreamReader reader = streamFactory.createXMLStreamReader(new FileInputStream(path_to_dict));

        Dict dict = new Dict();
        Grammeme current_grammeme = new Grammeme();

        Lemma current_lemma = new Lemma();

        enum parent_tag{grammeme, restr, lemma, none, lemma_l, lemma_f}
        parent_tag current = parent_tag.none;
        for (; reader.hasNext(); reader.next()) {
            int eventType = reader.getEventType();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT -> {
                    switch (reader.getLocalName()) {
                        case "dictionary" -> {
                            assert reader.getAttributeCount() == 2;
                            //System.out.println("version: " + reader.getAttributeValue(0) + ", revision: " + reader.getAttributeValue(1));
                        }
                        case "grammemes" -> {
                            //System.out.println("started grammemes");
                        }
                        case "restrictions" -> {
                            //System.out.println("started restrictions");
                        }
                        case "lemmata" -> {
                            //System.out.println("started lemmata");
                        }
                        case "restr" -> current = parent_tag.restr;
                        case "grammeme" -> {
                            current = parent_tag.grammeme;
                            current_grammeme = new Grammeme();
                        }
                        case "lemma" -> {
                            current = parent_tag.lemma;
                            current_lemma = new Lemma();
                            assert reader.getAttributeCount() == 2 && reader.getAttributeLocalName(0).equals("id");
                            current_lemma.id = reader.getAttributeValue(0);
                        }
                        // grammeme
                        case "name" -> {
                            assert current == parent_tag.grammeme;
                            reader.next();
                            assert reader.getEventType() == XMLStreamConstants.CHARACTERS;
                            current_grammeme.name = reader.getText().trim();
                        }
                        case "alias" -> {
                            assert current == parent_tag.grammeme;
                            reader.next();
                            assert reader.getEventType() == XMLStreamConstants.CHARACTERS;
                            current_grammeme.alias = reader.getText().trim();
                        }
                        case "description" -> {
                            assert current == parent_tag.grammeme;
                            reader.next();
                            assert reader.getEventType() == XMLStreamConstants.CHARACTERS;
                            current_grammeme.description = reader.getText().trim();
                        }
                        // restr
                        case "left", "right" -> {
                            assert current == parent_tag.restr;
                        }
                        // lemma
                        case "l" -> {
                            current = parent_tag.lemma_l;
                            assert reader.getAttributeCount() == 1 && reader.getAttributeLocalName(0).equals("t");
                            current_lemma.init = new Lemma.WordForm(reader.getAttributeValue(0));
                            current_lemma.forms = new ArrayList<>();
                        }
                        case "g" -> {
                            assert current == parent_tag.lemma_l || current == parent_tag.lemma_f;
                            assert reader.getAttributeCount() == 1 && reader.getAttributeLocalName(0).equals("v");
                            if(current == parent_tag.lemma_l){
                                current_lemma.init.grammemes.add(reader.getAttributeValue(0));
                            }
                            else{
                                current_lemma.forms.get(current_lemma.forms.size()-1).grammemes.add(reader.getAttributeValue(0));
                            }
                        }
                        case "f" -> {
                            current = parent_tag.lemma_f;
                            assert reader.getAttributeCount() == 1 && reader.getAttributeLocalName(0).equals("t");
                            current_lemma.forms.add(new Lemma.WordForm(reader.getAttributeValue(0)));
                        }
                        //default -> System.out.println("Unknown property: " + reader.getLocalName());
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    switch (reader.getLocalName()) {
                        case "grammeme" -> {
                            assert current == parent_tag.grammeme;
                            dict.grammemes.add(current_grammeme);
                            current = parent_tag.none;
                        }
                        case "lemma" -> {
                            assert current == parent_tag.lemma;
                            dict.lemmata.add(current_lemma);
                            current = parent_tag.none;
                        }
                        case "restr" -> {
                            assert current == parent_tag.restr;
                            current = parent_tag.none;
                        }
                        case "grammemes" -> {
                            //System.out.println("finished grammemes, cnt: " + dict.grammemes.size());
                        }
                        case "restrictions" -> {
                            //System.out.println("finished restrictions");
                        }
                        case "lemmata" -> {
                            //System.out.println("finished lemmata, cnt: " + dict.lemmata.size());
                            return dict;
                        }
                        case "l", "f" -> {
                            current = parent_tag.lemma;
                        }
                        case "g" -> {}
                        case "left", "right" -> {
                            assert current == parent_tag.restr;
                        }
                        case "alias", "description", "name" -> {
                            assert current == parent_tag.grammeme;
                        }
                        default -> {
                            //System.out.println("Unknown end tag: " + reader.getLocalName());
                        }
                    }
                }
            }
        }
        return dict;
    }
}
