import java.util.ArrayList;

public class Lemma {
    public String id;
    public WordForm init;
    public ArrayList<WordForm> forms;
    @Override
    public String toString(){
        return "#lemma " + id + " " + init +
                "\n\tforms: [" + forms + "]";
    }
    public Lemma(){}
    public Lemma(String init){
        this.init = new WordForm(init);
    }
    static class WordForm {
        final String t;
        final ArrayList<String> grammemes;
        public WordForm(String t){
           this.t = t;
           grammemes = new ArrayList<>();
        }
        public String toString(){
            return "\"" + t + "\": " + grammemes;
        }
    }
}
