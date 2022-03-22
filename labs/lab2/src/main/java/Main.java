import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    static ArrayList<String> to_skip = new ArrayList<>(
            Arrays.asList(",", ".", "!", ":", "?", "(", ")", "\"", ";", "«", "»", ";", "/"));
    public static void main(String[] args) throws IOException, XMLStreamException {
        ArrayList<String> texts = readCorpus("../../corp/corp/out.txt");
        ArrayList<ArrayList<String>> tokenized_texts = new ArrayList<>();
        for(var text : texts){
            tokenized_texts.add(tokenize(text));
        }
        System.out.println("Всего текстов: " + texts.size());
        System.out.println("Всего токенов: " + tokenized_texts.stream().map(x -> x.stream().filter(y -> !to_skip.contains(y)).count()).reduce(0L, Long::sum));

        Dict dict = parse_dict("../../dict/annot.opcorpora.xml/dict.opcorpora.xml");
        Map<String, ArrayList<Lemma>> form_to_lemmas = create_form_to_lemmas(dict.lemmata);

        ArrayList<ArrayList<WordInText>> lemmatized_texts = new ArrayList<>();
        for(var tokenized_text : tokenized_texts){
            lemmatized_texts.add(dumb_lemmatize_text(form_to_lemmas, tokenized_text));
        }

        int context_length = 3;
        String input = "огород";
        var lemmatized_input = dumb_lemmatize_text(form_to_lemmas, tokenize(input));
        for(var ltext : lemmatized_texts){
            boolean ltext_contains_phrase = false;
            for(int i = 0 ; i < ltext.size() - lemmatized_input.size() ; i++){
                // compare window [i; i+lemmatized_input.size)
                boolean bad = false;
                for(int j = i ; j < i + lemmatized_input.size() ; j++){
                    if(ltext.get(j).word.equals(lemmatized_input.get(j-i).word)){
                        continue;
                    }
                    boolean equal = ltext.get(j).possible_lemmas != null &&
                            lemmatized_input.get(j-i).possible_lemmas != null;
                    if(!equal){
                        bad = true;
                        break;
                    }
                    var text_lemmas = ltext.get(j).possible_lemmas;
                    var input_lemmas = lemmatized_input.get(j-i).possible_lemmas;
                    text_lemmas = new ArrayList<>(text_lemmas.stream().filter(x -> input_lemmas.stream().anyMatch(y -> x == y)).toList());
                    if(text_lemmas.isEmpty()){
                        bad = true;
                        break;
                    }
                }
                if(!bad){
                    ltext_contains_phrase = true;
                    // left context
                    int c = i;
                    System.out.print("< ");
                    ArrayList<String> left_context = new ArrayList<>();
                    while(c >= 0 && !to_skip.contains(ltext.get(c).word) && (i - c < context_length)){
                        left_context.add(ltext.get(c).word);
                        c--;
                    }
                    Collections.reverse(left_context);
                    left_context.forEach(x -> System.out.print(x + " "));

                    // right context
                    c = i;
                    System.out.print("\n> ");
                    while(c < ltext.size() && !to_skip.contains(ltext.get(c).word) && (c - i < context_length)){
                        System.out.print(ltext.get(c).word + " ");
                        c++;
                    }
                    System.out.println();
                }
            }
            if(ltext_contains_phrase){
            }
        }

    }

    private static void write_out(ArrayList<LemmaInfo> sorted) throws IOException {
        try(FileWriter fw = new FileWriter("dict.txt")) {
            for (var lemma : sorted) {
                fw.write(lemma.toString() + "\n");
            }
        }
    }

    private static ArrayList<WordInText> dumb_lemmatize_text(Map<String, ArrayList<Lemma>> form_to_lemmas, ArrayList<String> tokenized_text) {
        ArrayList<WordInText> lemmatized_text = new ArrayList<>();
        for (String word : tokenized_text) {
            if(to_skip.contains(word) || !form_to_lemmas.containsKey(word)) {
                lemmatized_text.add(new WordInText(word, null));
            }
            else {
                lemmatized_text.add(new WordInText(word, form_to_lemmas.get(word)));
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
                            System.out.println("version: " + reader.getAttributeValue(0) + ", revision: " + reader.getAttributeValue(1));
                        }
                        case "grammemes" -> {
                            System.out.println("started grammemes");
                        }
                        case "restrictions" -> {
                            System.out.println("started restrictions");
                        }
                        case "lemmata" -> {
                            System.out.println("started lemmata");
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
                        default -> System.out.println("Unknown property: " + reader.getLocalName());
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
                            System.out.println("finished grammemes, cnt: " + dict.grammemes.size());
                        }
                        case "restrictions" -> {
                            System.out.println("finished restrictions");
                        }
                        case "lemmata" -> {
                            System.out.println("finished lemmata, cnt: " + dict.lemmata.size());
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
                            System.out.println("Unknown end tag: " + reader.getLocalName());
                        }
                    }
                }
            }
        }
        return dict;
    }
}
