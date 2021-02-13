import java.io.Serializable;
public class statoUtente implements Serializable { //Classe utilizzata per avere informazioni su: Utente-Stato && Carta-ListaCorrente
    String nomeUtente;
    String stato;

    public statoUtente(String nomeUtente, String stato){
        this.nomeUtente=nomeUtente; //NomeUtente o NomeCarta
        this.stato=stato; //StatoUtente o StatoCarta
    }

    public statoUtente(){

    }

    public String getStato(){
        return this.stato;
    }

    public String getNomeUtente(){
        return this.nomeUtente;
    }

    public void setStato(String status){
        this.stato=status;
    }

    public void setNomeUtente(String nomeUtente){
        this.nomeUtente=nomeUtente;
    }

}
