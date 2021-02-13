import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Scanner;


@SuppressWarnings("unchecked")
public class ClientMain extends RemoteObject implements RMIEventInterface {
    private static final long serialVersionUID = 3330110084717693575L;
    private ArrayList<statoUtente> allUser; //Utenti iscritti a WORTH
    private String nomeUtente; //nomeUtente
    private ArrayList<String> allProject;
    private static final int RMIport= 19999; //Porta RMI
    private static final int TCPport= 16161; //Porta TCP
    private static final String indirizzoServer = "127.0.0.1"; //Indirizzo del server
    private final ArrayList<indirizziMulticast> multicastSockets; //Informazioni sui progetti



    public ClientMain(){
        super();
        this.nomeUtente= null;
        this.multicastSockets= new ArrayList<>();
    }


    public void start(){
        SocketChannel canaleSocket;
        boolean termina = true;
        boolean online = false;
        try{

            //Preparazione alla registrazione RMI
            Registry registro = LocateRegistry.getRegistry(RMIport);
            ServerMainInterfaceRMI stub = (ServerMainInterfaceRMI) registro.lookup("ServerRMI");
            Scanner read = new Scanner(System.in);
            canaleSocket= SocketChannel.open();
            canaleSocket.connect(new InetSocketAddress(indirizzoServer,TCPport)); //connessione al server

            RMIEventInterface callbackObj= this;
            RMIEventInterface stubCallBack = (RMIEventInterface) UnicastRemoteObject.exportObject(callbackObj,0);

            byte[] buffer= new byte [8192];
            while(termina){
                String comando = read.nextLine(); //Lettura del comando da linea di comando
                String [] token = comando.split(" "); //divisione del comando da inviare al server
                if(token[0].toLowerCase().equals("register")) { //vedere il tipo di comando richiesto dal client
                    String answer = null;
                    if (token.length > 3)
                        answer = "Errore: troppi argomenti passati come parametro";
                    else answer = stub.registrazione(token[1], token[2]); //Registrazione tramite RMI
                    System.out.println(answer);
                }

                //Prima di inviare ogni comando ci sarà un controllo per verificare se l'operazione può essere mandata al server


                //Caso login
                else if(token[0].toLowerCase().equals("login")){
                    if(online)
                        System.out.println("Errore, utente già registrato");
                    else{
                        boolean check = login(comando,canaleSocket);
                        if(check) {
                            online = true;
                            System.out.println("Registrazione per Callbacks");
                            stub.registerForCallback(stubCallBack,token[1]);
                        }
                        else{
                            System.out.println("Problemi nella registrazione");
                        }
                    }
                }

                //caso Logout
                else if(token[0].toLowerCase().equals("logout")){
                    boolean check = false;
                    if(!online)
                        System.out.println("Errore, utente non ancora connesso");
                    else {
                        if (token.length != 2)
                            System.out.println("Errore nel passaggio dei parametri");
                            //Scrittura sulla socket e lettura sulla socket
                        else {
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            String answer = ((String) obj.readObject()).trim();
                            if (answer.equals("ok")) {
                                System.out.println("Logout effettuato con successo!");
                                check = true;
                                nomeUtente = null;
                            } else {
                                System.out.println(answer);
                                check = false;
                            }
                        }
                        if (check) { //Se è andato a buon fine
                            online = false;
                            System.out.println("Logout per Callbacks");
                            stub.unregisterForCallback(stubCallBack);
                        }
                    }
                }

                //Caso listuser
                //Non interpella il server, stampe le informazione degli utenti controllando l'array allUsers
                else if(token[0].toLowerCase().equals("listusers")){
                    //controllo se l'utente che richiede il sevizio è online
                    if(!online)
                        System.out.println("Errore, bisogna effettuare il login");
                    else{
                        //controllo che il comando non abbia altri argomenti
                        if(token.length>1)
                            System.out.println("Passare correttamente i paramentri");
                        else{
                            //Stampo tutti gli utenti registrati al sistema (online/offline)
                            for(statoUtente user: allUser){
                                System.out.println("Utente: " + user.getNomeUtente() +" -Stato:"+ user.getStato() );
                            }
                        }
                    }
                }

                //Caso listOnlineuser
                //Non interpella il server, stampe le informazione degli utenti controllando l'array allUsers
                else if(token[0].toLowerCase().equals("listonlineusers")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere prima online");
                    else{
                        if(token.length>1)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            for(statoUtente onlineUser: allUser){
                                if(onlineUser.getStato().equals("online"))
                                    System.out.println("- Utente: " + onlineUser.getNomeUtente() +" -Stato:"+ onlineUser.getStato());
                            }
                        }
                    }
                }

                //Caso listProjects
                else if(token[0].toLowerCase().equals("listprojects")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere online");
                    else{
                        if(token.length>1)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            //Lettura sulla socket
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            Risultati<String> recive= (Risultati<String>) obj.readObject();
                            if(recive.getLista()==null)
                                System.out.println("Non ci sono progetti presenti al momento");
                            else{
                                for(String progetti: recive.getLista()){
                                    System.out.println("Progetto: " + progetti);
                                }
                            }
                        }
                    }
                }

                //Caso createProject
                else if(token[0].toLowerCase().equals("createproject")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere online");
                    else{
                        if(token.length!=2)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            String answer = ((String) obj.readObject()).trim();
                            if(answer.equals("ok")) { //Caso in cui il comando abbia avuto successo
                                System.out.println("Progetto creato con successo");
                            }
                            else
                                System.out.println(answer);
                        }
                    }
                }

                //Caso addMember
                else if(token[0].toLowerCase().equals("addmember")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere registrato");
                    else{
                        if(token.length<=2)
                            System.out.println("Passare correttamente i paramentri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            String answer = ((String) obj.readObject()).trim();
                            if(answer.equals("ok")) //Caso in cui il comando abbia avuto successo
                                System.out.println("Membro aggiunto con successo");
                            else System.out.println(answer);
                        }
                    }
                }


                //Caso showMembers
                else if(token[0].toLowerCase().equals("showmembers")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere online");
                    else{
                        if(token.length<=1)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            ArrayList<String> answer = (ArrayList<String>) obj.readObject();
                            if(answer.size()!=0){ //Verficare se la lista ricevuta non sia vuota
                                for(String member: answer){
                                    System.out.println("-Membro: " + member);
                                }
                            }
                            else
                                System.out.println("Errore nella lista dei progetti");
                        }
                    }
                }

                //Caso showCards: Stampa le carte di un progetto
                else if(token[0].toLowerCase().equals("showcards")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere online");
                    else{
                        if(token.length!=2)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            Risultati<statoUtente> answer = (Risultati<statoUtente>) obj.readObject();
                            if(answer.getLista()==null) //Verificare che la lista ottenuta non sia null
                                System.out.println(answer.getAnswer());
                            else{
                                for(statoUtente card: answer.getLista())
                                    System.out.println("Carta: "+card.getNomeUtente() + " Stato: " + card.getStato());
                            }
                        }
                    }
                }

                //Caso showcard: Stampa il nome dela card la sua descrizione e il suo stato
                else if(token[0].toLowerCase().equals("showcard")){
                    if(!online){
                        System.out.println("Errore, l'utente deve essere online");
                    }
                    else{
                        if(token.length!=3)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            ArrayList<String> answer = (ArrayList<String>) obj.readObject();
                            if(answer == null) //Verificare che sia andata a buon fine
                                System.out.println("Controllare se il progetto esiste o che la carta esiste");
                            else{
                               ArrayList<String> informazioni = answer;
                               System.out.println("Carta: "+ informazioni.get(0) + "|| Descizione: " +informazioni.get(1)+ " || Stato: "+ informazioni.get(2));
                            }
                        }
                    }
                }


                //caso addcard: richiede al server l'aggiunta di una card
                else if(token[0].toLowerCase().equals("addcard")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere online");
                    else{
                        if(token.length!=4)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            comando = comando.trim();
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj =  new ObjectInputStream(canaleSocket.socket().getInputStream());

                            String answer = (String) obj.readObject();
                            if(answer.equals("ok")) //Verificare che sia andata a buon fine
                                System.out.println("Carta aggiunta con successo");
                            else System.out.println(answer);
                        }
                    }
                }

                //caso movecard: richiede al server di muovere una card da una lista all'altra
                else if(token[0].toLowerCase().equals("movecard")){
                    if(!online){
                        System.out.println("Errore, l'utente deve essere online");
                    }
                    else{
                        if(token.length!=5)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());

                            String answer = (String) obj.readObject();

                            if(answer.equals("ok")) //Verificare che sia andata a buon fine
                                System.out.println("Carta spostata con successo");
                            else System.out.println(answer);
                        }
                    }
                }

                //caso getCardhistory: richiede il ciclo di vita di una carta
                else if(token[0].toLowerCase().equals("getcardhistory")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere online");
                    else{
                        if(token.length!=3)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());

                            ArrayList<String> answer = (ArrayList<String>) obj.readObject();
                            if(answer == null) //Verificare che la richiesta sia andata a buon fine
                                System.out.println("Controllare i parametri passati");
                            else{
                                System.out.println("Ciclo vita della carta /***");
                                for(String cicloVita: answer)
                                    System.out.println("Carta nello stato :" + cicloVita + "-> ");
                                System.out.println("***/");
                            }
                        }
                    }
                }





                //caso cancelProject: richiede al server la canellazione di un progetto
                else if(token[0].toLowerCase().equals("cancelproject")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere online");
                    else{
                        if(token.length!=2)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            String answer = (String) obj.readObject();
                            if(answer.equals("ok")) //Verificare che sia andata a buon fine
                                System.out.println("Progetto cancellato correttamente");
                            else System.out.println(answer);
                        }
                    }
                }

                //caso readChat: richiesta al server per leggere i messaggi di una chat
                else if(token[0].toLowerCase().equals("readchat")){
                    if(!online)
                        System.out.println("Errore, l'utente deve essere online");

                    else{
                        if(token.length!=2)
                            System.out.println("Passare correttamente i parametri");
                        else{
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            String answer = (String) obj.readObject();
                            if(answer.equals("ok")){ //verificare che sia andata a buon fine
                                boolean member = false;
                                MulticastSocket sock = null; //Recupero informazioni del progetto dove leggere i messaggi
                                for(indirizziMulticast ms: multicastSockets){
                                    if(token[1].equals(ms.getNomeProgetto())) {
                                        sock= ms.getSocket();
                                        member = true;
                                    }
                                }
                                if(!member)
                                    System.out.println("Non sei membro del progetto");
                                else {
                                    boolean terminaWhile = true;
                                    DatagramPacket datagram;
                                    while (terminaWhile) {
                                        datagram = new DatagramPacket(buffer, buffer.length);
                                        try { //Ricezione dei messaggi
                                            sock.receive(datagram);
                                            String messaggio = new String(datagram.getData(), 0, datagram.getLength());
                                            System.out.println(messaggio);
                                        } catch (SocketTimeoutException e) {
                                            System.out.println("-------Finiti i messaggi-------");
                                            terminaWhile = false;


                                        }
                                    }
                                }
                            }
                            else System.out.println(answer);
                        }
                    }
                }

                //caso sendchatmsg: Invio di un messaggio sulla chat del progetto
                else if (token[0].toLowerCase().equals("sendchatmsg")) {
                    if (!online)
                        System.out.println("Errore, l'utente deve essere online");
                    else {
                        if (token.length!=2)
                            System.out.println("Passare i parametri correttamente");

                        else {
                            //Richiesta al server e lettura sulla socket
                            canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));

                            ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
                            String answer = (String) obj.readObject();

                            if (answer.equals("ok")) {

                                String messaggio = null;
                                System.out.println("Inserisci il messaggio da inviare sulla chat del progetto:");
                                messaggio = read.nextLine();
                                messaggio = nomeUtente + ": " + messaggio;
                                byte[] buff = messaggio.getBytes();
                                DatagramPacket datagram;

                                boolean check = false;
                                MulticastSocket sock = null;
                                String ip = null;
                                int port = 0;
                                //recupero delle informazioni che serviranno per inviare il messaggio
                                for (indirizziMulticast ms : multicastSockets) {
                                    if (token[1].equals(ms.getNomeProgetto())) {
                                        sock = ms.getSocket();
                                        ip = ms.getIndirizzoIP();
                                        port = ms.getPorta();
                                        check = true;
                                    }
                                }
                                if (check) {
                                    System.out.println("Invio del messaggio");
                                    datagram = new DatagramPacket(buff, buff.length, InetAddress.getByName(ip), port);
                                    sock.send(datagram);
                                } else System.out.println("Non sei membro del progetto");
                            }
                              else System.out.println(answer);
                        }
                    }
                }

                //caso help: Lista dei comandi disponibili
                else if(token[0].toLowerCase().equals("help")){
                    if(token.length>1)
                        System.out.println("riprova");
                    else
                        help();

                }

                //Uscita dal servizio WORTH
                else if(token[0].toLowerCase().equals("esc")) {
                    if (online)
                        System.out.println("Devi procedere al logout prima di terminare");
                        //Annuncio al server la volontà di chiudere la connesione creata
                    else {
                        canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
                        //chiusura del ciclo
                        termina = false;
                        System.out.println("uscita effettuata!");
                    }
                }


                //Caso in cui nessun servizio offerto è stato richiesto
                else{
                    System.out.println("Richiedere uno dei servizi offerti dal server");
                }


            //chiusura ciclo
            }
            System.out.println("CALLBACK: Unregistering for callback");
            stub.unregisterForCallback(stubCallBack);
            System.exit(0);





        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //metodo che mi permette di effettuare il login
    private boolean login(String comando, SocketChannel canaleSocket) throws IOException, ClassNotFoundException {
        String[] token = comando.split(" ");
        risultatiLogin<statoUtente> loginResult;
        //Scrittura sulla socket
        canaleSocket.write(ByteBuffer.wrap(comando.getBytes(StandardCharsets.UTF_8)));
        //Lettura sulla socket
        ObjectInputStream obj = new ObjectInputStream(canaleSocket.socket().getInputStream());
        //Salavataggio oggetto ricevuto sulla socket
        loginResult = (risultatiLogin<statoUtente>) obj.readObject();
        //Controllo se il server ha dato il consenso
        if(loginResult.getAnswer().equals("ok")) {
            System.out.println("Login effettuato con successo!");
            allUser = loginResult.getLista();
            //Vedo il nome dell'utente;
            nomeUtente= token[1];
            //verifico ci siano progetti dove il nuovo utente possa connettersi
            if(loginResult.getMulticastList()!=null){
                for(indirizziMulticast indirizzo: loginResult.getMulticastList()){
                    MulticastSocket msocket;
                    try{ //il client si collega ai progetto dove è membro
                        msocket= new MulticastSocket(indirizzo.getPorta());
                        msocket.joinGroup(InetAddress.getByName(indirizzo.getIndirizzoIP()));
                        msocket.setSoTimeout(2000);
                        multicastSockets.add(new indirizziMulticast(indirizzo.getNomeProgetto(),indirizzo.getIndirizzoIP(),indirizzo.getPorta(),msocket));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return true;
        }
        else System.out.println(loginResult.getAnswer());
        return false;
    }

    public void help(){
        System.out.println("----------**********----------");
        System.out.println("LISTA DEI COMANDI DISPONIBILI IN WORTH. -> I COMANDI NON SONO CASESENSITIVE");
        System.out.println("register <username> <password> -> permette all'utente di potersi iscrivere al servizio");
        System.out.println("login <username> <password> -> permette ad un utente già registrato di connettersi al servizio");
        System.out.println("logout <username> -> permette all'utente di procedere al logout dal servizio");
        System.out.println("listusers -> permette di avere la lista degli utenti iscritti al servizio");
        System.out.println("listonlineusers -> permette di avere la lista degli utenti online ");
        System.out.println("listprojects -> permette di avere la lista dei progetti attivi");
        System.out.println("createproject <projectName> -> permette di poter creare un progetto");
        System.out.println("addmember <projectName> <nomeUtente> -> permette di aggiungere un nuovo membro al progetto");
        System.out.println("showmembers <projectName -> permette di avere la lista degli utenti all'interno del progetto");
        System.out.println("showcards <projectName> -> permette di avere una lista delle carti del progetto");
        System.out.println("showcard <projectName> <cardName> -> permette di avere informazioni riguardante una specifica carta nel progetto");
        System.out.println("addcard <projectName> <CardName> -> permette di aggiungere una nuova carta all'interno del progetto ");
        System.out.println("movecard <projectName> <cardName> <listaDiPartenza> <ListadiDestinazione>  -> permette di spostare una determinata carta da una lista all'altra");
        System.out.println("getcardhistory <projectName> <cardName> -> permette di verificare il ciclo di vita di una carta all'interno di un determinato progetto");
        System.out.println("readchat <projectName> -> permette di leggere i messaggi inviati su una chat del progetto");
        System.out.println("sendchatmsg <projectName> -> permette di inviare un messaggio sulla chat del progetto");
        System.out.println("cancelprojcet <projectName> -> permette di cancellare un progetto dal servizio ");
        System.out.println("esc -> permette di uscire dal servizio");
        System.out.println("SCEGLI UNO DEI COMANDI CHE SONO STATI ELENCATI!");
        System.out.println("NB: la lista di partenza e la lista di destinazione devono essere passate in maiuscolo!");
        System.out.println("----------**********----------");
    }




    @Override
    //Aggiornamento delle informazioni della lista utenti
    public void notifyStatus(String nickname, String stato) throws RemoteException {
        boolean check = false;
        System.out.println("CALLBACKS: aggiornamento lista utenti ");
        for(statoUtente result : allUser)
            if(result.getNomeUtente().equals(nickname)) {
                check = true;
                if(!result.getStato().equals(stato))
                    result.setStato(stato);
            }
        if(!check) allUser.add(new statoUtente(nickname,stato));
    }

    @Override

    //Aggiornamento delle informazioni  nella creazione di un nuovo progetto
    public void notifyChat(String nomeProgetto,String indirizzo, int porta) throws RemoteException {
        System.out.println("CALLBACK: aggiornamento chat");
        MulticastSocket sock;
        try{
            sock = new MulticastSocket(porta);
            sock.joinGroup(InetAddress.getByName(indirizzo));
            sock.setSoTimeout(2000);
            multicastSockets.add(new indirizziMulticast(nomeProgetto,indirizzo,porta,sock));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    //Aggiornamento delle informazioni nella cancellazione di un nuovo progetto
    public void notifyProgetto(String indirizzo, int porta) throws RemoteException {
        indirizziMulticast cancellazione = null;
        System.out.println("CALLBACK: cancellazione progetto");
        try{
            for(indirizziMulticast sock : multicastSockets) {
                if (sock.getIndirizzoIP().equals(indirizzo)) {
                    try {
                        sock.getSocket().leaveGroup(InetAddress.getByName(indirizzo));
                        cancellazione = sock;
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (cancellazione != null)
                multicastSockets.remove(cancellazione);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String [] args){
        ClientMain client = new ClientMain();
        client.start();
    }
}
