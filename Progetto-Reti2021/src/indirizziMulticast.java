import java.io.Serializable;
import java.net.MulticastSocket;

public class indirizziMulticast implements Serializable { //classe utilizzate per avere informazioni sui progetti in WORTH
    private String indirizzoIP;
    private int porta;
    private String nomeProgetto;
    private MulticastSocket socket;

    public indirizziMulticast(String nomeProgetto, String indirizzoIP, int porta, MulticastSocket socket){
        this.nomeProgetto= nomeProgetto;
        this.indirizzoIP=indirizzoIP;
        this.porta=porta;
        this.socket=socket;
    }

    public String getNomeProgetto(){
        return this.nomeProgetto;
    }

    public String getIndirizzoIP(){
        return this.indirizzoIP;
    }

    public int getPorta(){
        return this.porta;
    }

    public MulticastSocket getSocket(){
        return this.socket;
    }

}
