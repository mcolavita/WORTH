import java.lang.reflect.Array;
import java.io.*;
import java.net.MulticastSocket;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;

public class Progetto {

    private String nome;
    private ArrayList<Card> cards;
    //Utilizzate 4 liste per il ciclo di vita della carta
    private ArrayList<String> TODO;
    private ArrayList<String> INPROGRESS;
    private ArrayList<String> TOBEREVISED;
    private ArrayList<String> DONE;
    private String indirizzoMulticast;
    private ArrayList<String> membri;
    private int porta;
    private MulticastSocket ms;





    public Progetto(String nome, String nickname, String indirizzoMulticast, int porta) throws IOException {
        this.nome = nome;
        this.cards= new ArrayList<>();
        this.TODO=new ArrayList<>();
        this.INPROGRESS=new ArrayList<>();
        this.TOBEREVISED=new ArrayList<>();
        this.DONE=new ArrayList<>();
        this.indirizzoMulticast=indirizzoMulticast;
        this.porta=porta;
        MulticastSocket ms = new MulticastSocket(porta);
        this.membri=new ArrayList<>();
        membri.add(nickname);

    }


    public Progetto(){
        this.nome = " ";
        this.cards= new ArrayList<>();
        this.TODO=new ArrayList<>();
        this.INPROGRESS=new ArrayList<>();
        this.TOBEREVISED=new ArrayList<>();
        this.DONE=new ArrayList<>();
        this.indirizzoMulticast= " ";
        this.porta=porta;
        MulticastSocket ms = null;
        this.membri=new ArrayList<>();
        membri.add(nome);
    }




    //Verifica se un membro si trova all'interno dell'array membri
    public boolean findMembro(String nickname){
        if(this.membri.contains(nickname))
            return true;
        else return false;

    }

    //Aggiunta di un nuovo mebro se non compare nell'array
    public String addMember(String nickname) {
        if(findMembro(nickname)) {
            return"ERROR";
        }
        membri.add(nickname);
        return "ok";
    }

    //Restituisce l'array di mebri del progetto
    public ArrayList<String> getMembers(){
        if(membri.isEmpty())
            return null;
        return this.membri;
    }

    //Aggiunge una carta al progetto
    public String addCard(String cardName,String descrizione) {
        for(Card card: cards) {
            if (card.getnomeCarta().equals(cardName))
                return "Carta già presente";
        }
        Card card = new Card(cardName,descrizione);
        cards.add(card);
        TODO.add(cardName);


        return "ok";
    }

    //Sposta la carta da una lista all'altra rispettando opportuni vincoli (listaPartenza e Destinazione deveno essere in maiuscolo)
    public String moveCard(String cardName, String partenza, String destinazione){
        if(!partenza.equals("TODO") && !partenza.equals("INPROGRESS") && !partenza.equals("TOBEREVISED") && !partenza.equals("DONE"))
            return "Errore lista di partenza!";
        if(!destinazione.equals("TODO") && !destinazione.equals("INPROGRESS") && !destinazione.equals("TOBEREVISED") && !destinazione.equals("DONE"))
            return "Errore lista destinazione";
        boolean checkCard = false;
        for(Card card: cards) {
            if (card.getnomeCarta().equalsIgnoreCase(cardName)) {
                checkCard = true;
                String status = card.getStato(); //Vedo lo stato in cui si trova la carta

                switch (status) { //Switch per vedere in che stato ci troviamo
                    //Per ogni caso ci sarà il controllo sui vincoli
                    //Quando una carta può spostarsi verrà cancellata dalla lista di partenza e verrà inserita nella lista di destinazione.
                    case "TODO":
                        if(!partenza.equals("TODO"))
                            return "Errore";
                        if (!TODO.contains(card.getnomeCarta()))
                            return "Errore, carta non presente!";

                        if(!destinazione.equals("INPROGRESS"))
                            return "Azione non consentita";

                        if (INPROGRESS.contains(card.getnomeCarta()))
                            return "Errore, carta già presente!";
                        INPROGRESS.add(card.getnomeCarta());
                        card.setStato("INPROGRESS");
                        TODO.remove(card.getnomeCarta());
                        card.addCicloVita("INPROGRESS");
                        break;


                    case "INPROGRESS":
                        if(!partenza.equals("INPROGRESS"))
                            return "Errore";
                        if (!INPROGRESS.contains(card.getnomeCarta()))
                            return "Errore, carta non trovata";

                        if (!destinazione.equals("TOBEREVISED") && !destinazione.equals("DONE"))
                            return "Errore destinazione";
                        String next = destinazione;
                        if (next.equals("TOBEREVISED")) {
                            TOBEREVISED.add(card.getnomeCarta());
                            card.setStato("TOBEREVISED");
                            card.addCicloVita("TOBEREVISED");
                        }
                        if (next.equals("DONE")) {
                            DONE.add(card.getnomeCarta());
                            card.setStato("DONE");
                            card.addCicloVita("DONE");
                        }
                        INPROGRESS.remove(card.getnomeCarta());
                        break;


                    case "TOBEREVISED":
                        if(!partenza.equals("TOBEREVISED"))
                            return "Errore";
                        if (!TOBEREVISED.contains(card.getnomeCarta()))
                            return "Errore, carta non trovata";
                        if (!destinazione.equals("INPROGRESS") && !destinazione.equals("DONE"))
                            return "Errore destinazione";
                        if (destinazione.equals("INPROGRESS")) {
                            INPROGRESS.add(card.getnomeCarta());
                            card.setStato("INPROGRESS");
                            card.addCicloVita("INPROGRESS");
                        }
                        if (destinazione.equals("DONE")) {
                            DONE.add(card.getnomeCarta());
                            card.setStato("DONE");
                            card.addCicloVita("DONE");
                        }
                        TOBEREVISED.remove(card.getnomeCarta());
                        break;


                    case "DONE":
                        if (!DONE.contains(card.getnomeCarta()))
                            return "Errore, carta non presente";
                        return "La carta non può essere più spostata";
                }
            }
        }
        return "ok";
    }


    //Restituisce il ciclo di vita di una carta
    public ArrayList<String> getCardHisotry(String cardName){
        ArrayList<String> history = new ArrayList<>();
        for(Card card: cards){
            if(card.getnomeCarta().equals(cardName))
                history=card.getCardHistory();
        }
        if(history.size()==0)
            return null;
        return history;
    }

    //verifica se il progetto può esssere cancellato, controllando che tutte le carte siano nello stato DONE
    public boolean isDone(){
        if(cards.isEmpty())
            return true;
        if(this.TODO.size()!=0 || this.TOBEREVISED.size()!=0 || this.INPROGRESS.size()!=0)
            return false;
        return true;
    }




    public ArrayList<Card> getCards(){
        return this.cards;
    }

    //Restiuisce le informazioni della carta
    public ArrayList<String> showCard(String cardName){
        if(cards.isEmpty())
            return null;

        ArrayList<String> info= new ArrayList<>();
        for(Card card: cards){
            if(card.getnomeCarta().equals(cardName))
                info= card.getInfo();
        }
        return info;
    }

    //Restiuisce i nomi della carta e i loro stati
    public ArrayList<statoUtente> getCardsName(){
        if(cards.isEmpty())
            return null;
        ArrayList<statoUtente> nomeCarte = new ArrayList<>();
        for(Card card : cards)
            nomeCarte.add(new statoUtente(card.getnomeCarta(),card.getStato()));
        return nomeCarte;
    }

    /**
     *
     * Informazioni generali sul progetto
     */


    //Indirizzo multicast del progetto
    public String getIndirizzoMulticast(){
        return this.indirizzoMulticast;
    }

    public String getNome(){
        return this.nome;
    }

    public String nomeProgetto(){
        return this.nome;
    }

    public int getPorta(){
        return this.porta;
    }

    public MulticastSocket getMs(){
        return ms;
    }

    public void setNome(String nome){
        this.nome=nome;
    }

    public ArrayList<String> getTODO(){
        return this.TODO;
    }

    public ArrayList<String> getINPROGRESS(){
        return this.INPROGRESS;
    }

    public ArrayList<String> getTOBEREVISED(){
        return this.TOBEREVISED;
    }

    public ArrayList<String> getDONE(){
        return this.DONE;
    }

    public void setCards(ArrayList<Card> cards){
        this.cards = cards;
    }

    public void setTODO(ArrayList<String> TODO){
        this.TODO = TODO;
    }

    public void  setINPROGRESS(ArrayList<String> INPROGRESS){
        this.INPROGRESS=INPROGRESS;
    }

    public void setTOBEREVISED(ArrayList<String> TOBEREVISED){
        this.TOBEREVISED=TOBEREVISED;
    }

    public void setDONE(ArrayList<String> DONE){
        this.DONE=DONE;
    }

    public void setIndirizzoMulticast(String indirizzoMulticast){
        this.indirizzoMulticast=indirizzoMulticast;
    }

    public void setMembri(ArrayList<String> Membri){
        this.membri=Membri;
    }

    public void setPorta(int porta){
        this.porta = porta;
    }

    public void setMs(MulticastSocket ms){
        this.ms = ms;
    }


}
