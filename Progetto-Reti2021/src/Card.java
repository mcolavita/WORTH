import java.io.Serializable;
import java.util.ArrayList;

public class Card implements Serializable {
    private String nomeCarta;
    private ArrayList<String> cardHistory;
    private String descrizione;
    private String stato;

    public Card(String nomeCarta, String descrizione){
        this.nomeCarta=nomeCarta;
        this.descrizione=descrizione;
        this.stato="TODO"; //Settata inizialmente a TODO non appena viene creata
        this.cardHistory= new ArrayList<>();
        cardHistory.add(stato);
    }

    public Card(){

    }
    //Restituisce il ciclo di vita di una carta
    public ArrayList<String> getCiclovita(){
        return this.cardHistory ;
    }

    //Aggiunge al ciclo di vita della carta lo stato corrente
    public void addCicloVita(String status){
        this.cardHistory.add(status);
    }


    public String getnomeCarta() {
        return nomeCarta;
    }

    public ArrayList<String> getInfo(){
        ArrayList<String> card = new ArrayList<>();
        card.add(nomeCarta);
        card.add(descrizione);
        card.add(stato);
        return card;
    }

    //Storico della carta
    public ArrayList<String> getCardHistory(){ //Gets card history list
        ArrayList<String> history = new ArrayList<>();
        for(String status : cardHistory)
            history.add(status);
        return history;
    }

    /**
     *
     * Informazioni di base della carta
     */


    //Restiuisce lo stato attuale della carta
    public String getStato(){
        return this.stato;
    }

    public String getDescrizione(){
        return this.descrizione;
    }

    public void setNomeCarta(String nomeCarta){
        this.nomeCarta = nomeCarta;
    }

    public void setCardHistory(ArrayList<String> cardHistory){
        this.cardHistory=cardHistory;
    }

    public void setDescrizione(String descrizione){
        this.descrizione=descrizione;
    }

    public void setStato(String ciclovita){
        this.stato=ciclovita;
    }



}
