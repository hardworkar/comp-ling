import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Document {
    List<Lemma> words = new ArrayList<>();
    Map<Entity, Double> tf_idf = new HashMap<>();
    final public Map<Entity, Integer> frequencies = new HashMap<>();
    final List<Entity> entities;
    public Document(ArrayList<ArrayList<Lemma>> lemmas, List<Entity> entities){
        for(var l : lemmas){
            words.add(l.get(0));
        }
        for(var e : entities){
            frequencies.put(e, 0);
            tf_idf.put(e, 0.0);
        }
        for(var e : entities){
            processEntity(e);
        }
        this.entities = entities;
    }

    // returns number of times Entity e occurs + all of its children occur
    private void traverseUp(Entity e){
        for(Entity h : e.hypernymies){
            frequencies.put(h, frequencies.get(h) + 1);
            traverseUp(h);
        }
    }
    private void processEntity(Entity e) {
        // inc synset
        for(Ngram n : e.synset) {
            for (int i = 0; i < words.size() - n.lemmas.size() + 1; i++) {
                if(words.subList(i, i + n.lemmas.size())
                        .equals(n.lemmas)){
                   frequencies.put(e, frequencies.get(e) + 1);
                   // inc hypernymies
                   traverseUp(e);
                }
            }
        }
    }
    public void calculateTF_IDF(Map<Entity, Double> idf){
        int frequencySum = frequencies.values().stream().reduce(Integer::sum).orElse(0);
        if(frequencySum > 0) {
            for (var e : entities) {
                tf_idf.put(e, (double)frequencies.get(e) / frequencySum * idf.get(e));
            }
        }
    }

    @Override
    public String toString(){
        /*
        String mapAsString = frequencies.keySet().stream()
                .map(key -> key.descriptor + "=" + frequencies.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
         */
        /*
        String mapAsString = tf_idf.keySet().stream()
                .map(key -> key.descriptor + "=" + tf_idf.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
        */
        return words.stream().map(x -> x.init.t).collect(Collectors.joining(" ")) + "\n";
    }

    public double getCos(Document other){
        var v1 = new ArrayList<>(this.tf_idf.values());
        var v2 = new ArrayList<>(other.tf_idf.values());
        double product = 0;
        double len1 = 0, len2 = 0;
        for(int i = 0 ; i < v1.size() ; i++){
           product += v1.get(i) * v2.get(i);
           len1 += v1.get(i) * v1.get(i);
           len2 += v2.get(i) * v2.get(i);
        }
        len1 = Math.sqrt(len1);
        len2 = Math.sqrt(len2);
        if(Math.abs(len1*len2) < 1e-9)
            return 0;
        return product / (len1 * len2);
    }
}
