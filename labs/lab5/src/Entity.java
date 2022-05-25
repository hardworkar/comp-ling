import java.util.ArrayList;
import java.util.List;

public class Entity {
    String descriptor;
    List<Ngram> synset = new ArrayList<>();
    List<Entity> hyponymies = new ArrayList<>();
    List<Entity> hypernymies = new ArrayList<>();

    List<String> hyponymiesDesc = new ArrayList<>();
    List<String> hypernymiesDesc = new ArrayList<>();
    public Entity(String descriptor){
        this.descriptor = descriptor;
    }

    @Override
    public String toString(){
        StringBuilder s = new StringBuilder();
        s.append("desc: ").append(descriptor).append("\n");
        s.append("synset: [");
        String prefix = "";
        for(var n : synset){
            s.append(prefix).append(n);
            prefix = ", ";
        }
        s.append("]\nhyponymies: [");
        prefix = "";
        for(var n : hyponymiesDesc){
            s.append(prefix).append(n);
            prefix = ", ";
        }
        s.append("]\nhypernymies: [");
        prefix = "";
        for(var n : hypernymiesDesc){
            s.append(prefix).append(n);
            prefix = ", ";
        }
        s.append("]\n");
        return s.toString();
    }
}
