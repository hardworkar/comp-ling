import java.util.*;

class NgramContext {
    Integer abs_freq = 0;
    Map<ArrayList<Lemma>, Integer> left_expands = new HashMap<>();
    Map<ArrayList<Lemma>, Integer> right_expands = new HashMap<>();
    Set<Integer> texts_mentioned = new HashSet<>();
}
