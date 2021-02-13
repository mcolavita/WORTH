import java.rmi.Remote;
import java.rmi.RemoteException;
public interface RMIEventInterface extends Remote {
    void notifyStatus(String nickname, String stato) throws RemoteException;
    void notifyChat(String nomeProgetto, String indirizzo, int porta) throws RemoteException;
    void notifyProgetto(String indirizzo, int porta) throws RemoteException;
}
