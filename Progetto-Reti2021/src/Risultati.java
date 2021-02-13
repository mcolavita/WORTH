import java.io.Serializable;
import java.util.ArrayList;

public class Risultati<T> implements Serializable {
    private String answer;
    private ArrayList<T> lista;


    public Risultati(String answer, ArrayList<T> lista){
        this.answer=answer;
        this.lista=lista;
    }

    public String getAnswer(){
        return this.answer;
    }

    public ArrayList<T> getLista(){
        return this.lista;
    }
}
