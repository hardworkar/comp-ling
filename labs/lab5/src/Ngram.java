import java.util.ArrayList;
import java.util.stream.Collectors;

public class Ngram {
    ArrayList<Lemma> lemmas = new ArrayList<>();
    @Override
    public String toString(){
        return lemmas.stream().map(x -> x.init.t).collect(Collectors.joining(" "));
    }
}
