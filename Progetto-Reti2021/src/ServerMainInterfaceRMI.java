import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerMainInterfaceRMI extends Remote {

    public String registrazione(String nomeUtente, String password ) throws RemoteException;

    public void registerForCallback (RMIEventInterface ClientInterface,String nickUtente) throws RemoteException;

    public void unregisterForCallback (RMIEventInterface ClientInterface) throws RemoteException;
}
