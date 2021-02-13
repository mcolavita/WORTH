import java.io.Serializable;
import java.sql.Array;
import java.util.ArrayList;

public class risultatiLogin<T> implements Serializable { //Classe utilizzata per fornire al cliente le informazioni necessari al login
    private String answer;
    private ArrayList<T> lista;
    private ArrayList<indirizziMulticast> multicastList;

    public risultatiLogin(String answer, ArrayList<T> lista, ArrayList<indirizziMulticast> multicastList){
        this.answer=answer;
        this.lista= lista;
        this.multicastList=multicastList;
    }

    public String getAnswer(){
        return this.answer;
    }

    public ArrayList<T> getLista(){
        return this.lista;
    }

    public ArrayList<indirizziMulticast> getMulticastList(){
        return this.multicastList;
    }


}
