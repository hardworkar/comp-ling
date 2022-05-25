import java.util.ArrayList;

public class Vector {
    public ArrayList<Double> values = new ArrayList<>();
    Vector(int dimension){
       for(int i = 0 ; i < dimension ; i++)
           values.add(0.0);
    }
}
