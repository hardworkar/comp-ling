import java.util.ArrayList;

public class WordInText {
    public final ArrayList<Lemma> possible_lemmas;
    public final String word;
    public WordInText(String word, ArrayList<Lemma> possible_lemmas){
        this.word = word;
        this.possible_lemmas = possible_lemmas;
    }
}
