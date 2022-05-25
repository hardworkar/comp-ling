import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Model {
    ArrayList<String> elems = new ArrayList<>();
    ArrayList<Ngram> ngrams = new ArrayList<>();
    ArrayList<String> grams = new ArrayList<>();
    boolean add(String s){
        this.elems.add(s);
        return true;
    }
    @Override
    public String toString(){
        StringBuilder out = new StringBuilder();
        if(!grams.isEmpty()){
            out.append("[");
            out.append(String.join(", ", grams));
            out.append("] ");
        }
        out.append(String.join(", ", elems));
        for(var ngram : ngrams){
            out.append(" [");
            String prefix = "";
            for(var lemma : ngram.lemmas) {
                out.append(prefix).append(lemma.init.t);
                prefix = ", ";
            }
            out.append("] ");
        }
        return out.toString();
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
        for(var ngram : ngrams){
            boolean found = false;
            for(int i = 0 ; i < sentence.words.size() - ngram.lemmas.size() + 1 ; i++){
                boolean possible_place = true;
               for(int j = 0 ; j < ngram.lemmas.size() ; j++){
                   if(!sentence.words.get(i + j).equals(ngram.lemmas.get(j))){
                       possible_place = false;
                       break;
                   }
               }
               if(possible_place)
                   found = true;
            }
            if(!found)
                return false;
        }
        for(var gram : grams){
            boolean found = false;
            for(var word : sentence.words){
                if(word.init.grammemes.size() > 0) {
                    if (word.init.grammemes.get(0).equals(gram)) {
                        found = true;
                        break;
                    }
                }
            }
            if(!found)
                return false;
        }
        return true;
    }
}
