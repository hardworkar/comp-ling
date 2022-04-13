import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class NgramContext {
    Integer freq = 0;
    Map<ArrayList<Lemma>, Integer> left_expands = new HashMap<>();
    Map<ArrayList<Lemma>, Integer> right_expands = new HashMap<>();
}
