import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    final static ArrayList<String> to_skip = new ArrayList<>(
            Arrays.asList(",", ".", "!", ":", "?", "(", ")", "\"", ";", "«", "»", ";", "/"));
    public static void main(String[] args) throws XMLStreamException {
        ArrayList<String> texts = readTextCorpus();
        ArrayList<ArrayList<String>> tokenized_texts = texts.stream().map(Main::tokenize).collect(Collectors.toCollection(ArrayList::new));

        System.out.println("Всего текстов: " + texts.size());
        System.out.println("Всего токенов: " + tokenized_texts.stream().map(x -> x.stream().filter(y -> !to_skip.contains(y)).count()).reduce(0L, Long::sum));

        Map<String, ArrayList<Lemma>> form_to_lemmas = getDictionary();

        ArrayList<ArrayList<ArrayList<Lemma>>> lemmatized_texts = tokenized_texts.stream().map(x -> lemmatize_text(form_to_lemmas, x)).collect(Collectors.toCollection(ArrayList::new));

        int ngram_length = 3;
        int RELATIVE_FREQ_THRESHOLD = 1;
        int NGRAM_FREQ_THRESHOLD = 3;
        Map<ArrayList<ArrayList<Lemma>>, NgramContext> ngram_stats = countContexts(ngram_length, lemmatized_texts);
        var sorted_ngrams = sort_context(ngram_stats);

        var filtered_ngrams = sorted_ngrams.stream().filter(ngram -> {
            var sorted_left = new LinkedList<>(ngram.getValue().left_expands.entrySet());
            sorted_left.sort(Comparator.comparingInt(Map.Entry::getValue));
            Collections.reverse(sorted_left);

            var sorted_right = new LinkedList<>(ngram.getValue().right_expands.entrySet());
            sorted_right.sort(Comparator.comparingInt(Map.Entry::getValue));
            Collections.reverse(sorted_right);

            Integer Fx = ngram.getValue().freq;
            Integer Fax = sorted_left.size() > 0 ? sorted_left.get(0).getValue() : 0;
            Integer Fxb = sorted_right.size() > 0 ? sorted_right.get(0).getValue() : 0;

            /*
            ngram.getKey().forEach(x -> System.out.print(x.get(0).init.t + " "));
            System.out.print("[" + Fx + "]");
            if(sorted_left.size() > 0) {
                System.out.print(" --> ");
                sorted_left.get(0).getKey().forEach(x -> System.out.print(x.init.t + " "));
                ngram.getKey().forEach(x -> System.out.print(x.get(0).init.t + " "));
            }
            System.out.println("[" + Fax + "]");
            */
            return (Fax / Fx < RELATIVE_FREQ_THRESHOLD && Fxb / Fx < RELATIVE_FREQ_THRESHOLD) && ngram.getValue().freq >= NGRAM_FREQ_THRESHOLD;
        }).toList();


        print_ngrams(filtered_ngrams);
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
        for(var ltext : lemmatized_texts){
            for(int i = 0; i < ltext.size() - ngram_length ; i++){
                int c = i;
                ArrayList<ArrayList<Lemma>> ngram = new ArrayList<>();
                while(c < ltext.size() && (c - i) < ngram_length){
                    ngram.add(ltext.get(c));
                    c++;
                }
                ngram_stats.putIfAbsent(ngram, new NgramContext());
                ngram_stats.get(ngram).freq++;

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
                while (ctx_idx < ltext.size() && (ctx_idx - (i+ngram_length) <= 1)) {
                    right_context.add(ltext.get(ctx_idx).get(0));
                    add_context(ngram_stats.get(ngram).right_expands, ctx_idx - i, false, right_context);
                    ctx_idx++;
                }

            }
        }
        return ngram_stats;
    }

    private static void print_ngrams(List<Map.Entry<ArrayList<ArrayList<Lemma>>, NgramContext>> sorted) {
        try(FileWriter fileWriter = new FileWriter("ngrams.txt")) {
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println("Всего устойчивых n-грамм: " + sorted.size());
            for(var ngram : sorted) {
                var sorted_left = new LinkedList<>(ngram.getValue().left_expands.entrySet());
                sorted_left.sort(Comparator.comparingInt(Map.Entry::getValue));
                Collections.reverse(sorted_left);

                var sorted_right = new LinkedList<>(ngram.getValue().right_expands.entrySet());
                sorted_right.sort(Comparator.comparingInt(Map.Entry::getValue));
                Collections.reverse(sorted_right);

                Integer Fx = ngram.getValue().freq;
                int Fax = sorted_left.size() > 0 ? sorted_left.get(0).getValue() : 0;
                int Fxb = sorted_right.size() > 0 ? sorted_right.get(0).getValue() : 0;

                ngram.getKey().forEach(x -> printWriter.print(x.get(0).init.t + " "));
                printWriter.print("[" + Fx + "]");
                if(sorted_left.size() > 0) {
                    printWriter.print(" --> ");
                    sorted_left.get(0).getKey().forEach(x -> printWriter.print(x.init.t + " "));
                    ngram.getKey().forEach(x -> printWriter.print(x.get(0).init.t + " "));
                    printWriter.print("[" + Fax + "]");
                }
                if(sorted_right.size() > 0) {
                    printWriter.print(" --> ");
                    ngram.getKey().forEach(x -> printWriter.print(x.get(0).init.t + " "));
                    sorted_right.get(0).getKey().forEach(x -> printWriter.print(x.init.t + " "));
                    printWriter.print("[" + Fxb + "]");
                }
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
        context_list.sort(Comparator.comparingInt(o -> o.getValue().freq));
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
