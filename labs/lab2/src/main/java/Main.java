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

public class Main {
    public static void main(String[] args) throws IOException, XMLStreamException {
        ArrayList<String> texts = readCorpus("../../corp/corp/out.txt");
        ArrayList<ArrayList<String>> tokenized_texts = tokenize(texts);
        System.out.println("Всего текстов: " + texts.size());
        ArrayList<String> to_skip = new ArrayList<>(
                Arrays.asList(",", ".", "!", ":", "?", "(", ")"));
        System.out.println("Всего токенов: " + tokenized_texts.stream().map(x -> x.stream().filter(y -> !to_skip.contains(y)).count()).reduce(0L, Long::sum));

        Dict dict = parse_dict("../../dict/annot.opcorpora.xml/dict.opcorpora.xml");
        Map<String, ArrayList<Lemma>> form_to_lemmas = create_form_to_lemmas(dict.lemmata);

        var lemmatized_texts = lemmatize_texts(form_to_lemmas, tokenized_texts);

        Map<Lemma, LemmaInfo> out_dictionary = getLemmaInfoMap(lemmatized_texts);

        ArrayList<LemmaInfo> sorted = new ArrayList<>(out_dictionary.values());
        Collections.sort(sorted);

        write_out(sorted);
    }

    private static void write_out(ArrayList<LemmaInfo> sorted) throws IOException {
        try(FileWriter fw = new FileWriter("dict.txt")) {
            for (var lemma : sorted) {
                fw.write(lemma.toString() + "\n");
            }
        }
    }

    private static Map<Lemma, LemmaInfo> getLemmaInfoMap(ArrayList<ArrayList<ArrayList<Lemma>>> lemmatized_texts) {
        int unsure = 0;
        int real_solutions = 0;
        int possible_solutions = 0;
        Map<Lemma, LemmaInfo> out_dictionary = new HashMap<>();
        for(var text : lemmatized_texts){
            for(ArrayList<Lemma> lemmas_for_word : text){
                if(lemmas_for_word.size() > 0 && lemmas_for_word.get(0).init.t.length() > 1){
                    real_solutions ++;
                    possible_solutions += lemmas_for_word.size();
                }
                if(lemmas_for_word.size() == 1) {
                    // неоднозначности нет
                    Lemma lemma = lemmas_for_word.get(0);
                    if(out_dictionary.containsKey(lemma)){
                        out_dictionary.get(lemma).cnt++;
                    }
                    else{
                        out_dictionary.put(lemma, new LemmaInfo(lemma));
                    }
                }
                else{
                    unsure++;
                }
            }
        }
        System.out.println("Не удалось однозначно определить лемму: " + unsure);
        System.out.println("Точность: " + (float) real_solutions / possible_solutions * 100);
        return out_dictionary;
    }

    private static ArrayList<ArrayList<ArrayList<Lemma>>> lemmatize_texts(Map<String, ArrayList<Lemma>> form_to_lemmas, ArrayList<ArrayList<String>> tokenized_texts) {
        int misses = 0;
        ArrayList<ArrayList<ArrayList<Lemma>>> lemmatized_texts = new ArrayList<>();
        ArrayList<String> to_skip = new ArrayList<>(
                Arrays.asList(",", ".", "!", ":", "?", "(", ")"));
        for (var text : tokenized_texts) {
            ArrayList<ArrayList<Lemma>> lemmas_for_text = new ArrayList<>();
            for (String word : text) {
                if(to_skip.contains(word))
                    continue;
                if (form_to_lemmas.containsKey(word)) {
                    // словоформа есть в словаре
                    lemmas_for_text.add(form_to_lemmas.get(word));
                } else {
                    misses++;
                }
            }
            lemmatized_texts.add(lemmas_for_text);
        }
        System.out.println("Не распознано токенов: " + misses);
        return lemmatized_texts;
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

    private static ArrayList<ArrayList<String>> tokenize(ArrayList<String> texts) {
        ArrayList<ArrayList<String>> out = new ArrayList<>();
        for(var text : texts) {
            out.add(new ArrayList<>(Arrays.stream(
                            text
                            .replace(",", "#,#")
                            .replace(".", "#.#")
                            .replace("!", "#!#")
                            .replace("?", "#?#")
                            .replace("'", "#?#")
                            .replace("(", "#(#")
                            .replace(")", "#)#")
                            .split("\\s+|#"))
                            .map(String::trim)
                            .filter(x -> x.length() > 0)
                            .map(x -> x.toLowerCase(Locale.ROOT)).toList()));
        }
        return out;
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
