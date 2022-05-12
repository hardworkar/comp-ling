import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Model {
    ArrayList<String> elems = new ArrayList<>();
    boolean add(String s){
        this.elems.add(s);
        return true;
    }
    @Override
    public String toString(){
        return String.join(", ", elems);
    }
    boolean accept(Sentence sentence, Map<String, String> semantics){
        Map<String, Boolean> presented = new HashMap<>();
        for(var elem : elems){
            presented.put(elem, false);
        }
        for(Lemma l : sentence.words){
            String lemmaId = l.id;
            if(semantics.containsKey(lemmaId)){
                String role = semantics.get(lemmaId);
                if(presented.containsKey(role)){
                    presented.put(role, true);
                }
            }
        }
        for(var e : presented.entrySet()){
            if(!e.getValue())
                return false;
        }
        return true;
    }
}
