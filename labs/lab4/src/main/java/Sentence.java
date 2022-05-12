import java.util.ArrayList;
import java.util.stream.Collectors;

public class Sentence {
    ArrayList<Lemma> words = new ArrayList<>();
    public String toString(){
        return words.stream().map(x -> x.init.t)
                .collect(Collectors.joining(" "));
    }
    boolean add(Lemma l){
        words.add(l);
        return true;
    }
}
