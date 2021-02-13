import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class ServerMain extends RemoteObject implements ServerMainInterfaceRMI {
    private static final long serialVersionUID = -932570530032326976L;
    private static final int RMIport = 19999; //Porta RMI
    private static final int TCPport = 16161; //Porta TCP
    private ArrayList<CallBackInfo> clients;
    private ArrayList<Utente> utenti; //Lista di utenti iscritti al servizio
    private ArrayList<Progetto> progetti; //Progetti attivi nel servizio
    private final ArrayList<indirizziMulticast> multicastSockets; //Informazioni per il multicast
    private final HashMap<SocketChannel,ArrayList<byte[]>> dataHashMap;
    private final Random rand;
    private final File directoryBackup;
    private final ObjectMapper objMapper;
    private File userFile,projectFile,MipGeneratorFile;



    public ServerMain(){
        super();
        //Creazione cartella Backup per la permanenza
        directoryBackup= new File("./Backup");
        this.objMapper= new ObjectMapper();
        objMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.multicastSockets= new ArrayList<>();
        dataHashMap = new HashMap<>();
        rand= new Random();
        clients = new ArrayList<>();
        restoreBackup();
    }

    public void restoreBackup(){
        /***
         * Questo metodo serve per andare a creare i file per progetti ed utenti per la permanenza del server.
         * Nel caso in cui non ci fossero i file, verrano creati all'avvio del server.
         */
        this.userFile = new File(directoryBackup + "/Utenti.json");
        this.projectFile = new File(directoryBackup + "/Progetti.json");

        System.out.println("Ripristino file di backup");
        if(!directoryBackup.exists())
            directoryBackup.mkdir();
        try{
            if(!userFile.exists()) {
                this.utenti = new ArrayList<>();
                userFile.createNewFile();
                objMapper.writeValue(userFile,utenti);
            }
            else
                this.utenti  = new ArrayList<>(Arrays.asList(objMapper.readValue(userFile,Utente[].class)));
            if(!projectFile.exists()){
                this.progetti= new ArrayList<>();
                projectFile.createNewFile();
                objMapper.writeValue(projectFile,progetti);
            }
            else {
                this.progetti = new ArrayList<>(Arrays.asList(objMapper.readValue(projectFile, Progetto[].class)));
                MulticastSocket sock;
                String ip;
                String nomeProgetto;
                int porta;
                for(Progetto progetto: progetti){
                    try{
                        ip=progetto.getIndirizzoMulticast();
                        porta = progetto.getPorta();
                        nomeProgetto = progetto.nomeProgetto();
                        sock = new MulticastSocket(porta);
                        sock.joinGroup(InetAddress.getByName(ip));
                        sock.setSoTimeout(2000);
                        multicastSockets.add(new indirizziMulticast(nomeProgetto,ip,porta,sock));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public void start() throws IOException {
        try {
            String comando;
            String[] token;
            String answer;
            boolean termina = true;
            List<byte[]> datas;
            ByteArrayOutputStream output;
            ObjectOutputStream objoutput;
            byte[] res;
            ServerSocketChannel canaleSocketServer;


            //Preparazione della socket lato server
            canaleSocketServer = ServerSocketChannel.open();
            canaleSocketServer.socket().bind(new InetSocketAddress(TCPport));
            canaleSocketServer.configureBlocking(false);
            //Preparazione al multicast, abbiamo bisogno del selettore
            Selector selector = Selector.open();
            canaleSocketServer.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("IL SERVER WORTH E' ONLINE");

            //Ciclo infinito dove vengono accettate le connessioni dai vari client e dove verrano eseguiti i comandi richiesti
            while (termina) {
                try {
                    selector.select();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    break;
                }

                Set<SelectionKey> chiaviSelezionate = selector.selectedKeys();
                Iterator<SelectionKey> iteratoreChiavi = chiaviSelezionate.iterator();
                while (iteratoreChiavi.hasNext()) {
                    SelectionKey chiave = iteratoreChiavi.next();
                    iteratoreChiavi.remove();

                    try {
                        //Vedere se è una nuova richiesta di connessione
                        if (chiave.isAcceptable()) {
                            ServerSocketChannel server = (ServerSocketChannel) chiave.channel();
                            SocketChannel client = server.accept();
                            System.out.println("WORTH: connessione accettata da client: " + client);
                            client.configureBlocking(false);
                            dataHashMap.put(client, new ArrayList<>());
                            client.register(selector, SelectionKey.OP_READ);
                            chiave.attach(null);
                        }
                        //Nel caso in cui il client è già registrato vedere la richiesta del comando da eseguire
                        else if (chiave.isReadable()) {
                            SocketChannel client = (SocketChannel) chiave.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(128); //alloco il buffer per leggere il comando
                            client.read(buffer);
                            comando = new String(buffer.array()).trim();
                            token = comando.split(" "); //Spezzo il comando così da avere operazione e parametri
                            String operation = token[0]; //comando da eseguire

                            //Caso login
                            if (operation.toLowerCase().equals("login")) {
                                risultatiLogin<statoUtente> result; //Lista di utenti e stati da inviare come risposta al client
                                if (token.length <= 2 || token.length > 3)
                                    result = new risultatiLogin<>("ERRORE: passare correttamente i parametri", null, null);
                                else {
                                    result = login(token[1], token[2]); //Passare nome utente e password
                                    if (result.getAnswer().equals("ok")) {
                                        System.out.println("Login corretto");
                                        chiave.attach(token[1]); //Memorizzo il nome utente del client
                                    }
                                }
                                //Preparazione all'invio dell'esito dell'operazione
                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }


                            //caso logout
                            else if(operation.toLowerCase().equals("logout")){
                                String send = null; //Risposta da inviare al client per dire se il logout ha avuto successo

                                if(token.length!=2)
                                    System.out.println("Passare correttamente i parametri");
                                else{
                                    if(((String) chiave.attachment()).equals(token[1])) { //Vedere se il client che chiede il logout è corretto
                                        send = logout(token[1]);
                                        if (send.equals("ok"))
                                            chiave.attach(null);
                                    }
                                }

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(send);
                                res = output.toByteArray();
                                datas.add(res);

                            }


                            //caso listusers
                            else if(operation.toLowerCase().equals("listusers")){
                                String result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=1)
                                    System.out.println("Passare correttamente i parametri");
                                else
                                    result = listusers();

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);


                            }

                            //caso listonlineusers
                            else if(operation.toLowerCase().equalsIgnoreCase("listonlineusers")){
                                String result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=1)
                                    System.out.println("Passare correttamente i parametri");
                                else
                                    result= listOnlineUsers();

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);

                            }

                            //caso listprojects
                            else if(operation.toLowerCase().equalsIgnoreCase("listprojects")){
                                Risultati<String> result = null; //Risposta da inviare al client per informarlo del successo o meno dell'operazione + lista di progetti
                                if(token.length!=1)
                                    System.out.println("Errore, passare correttamente i parametri");
                                else result=listProjects((String) chiave.attachment());
                                if(result.getAnswer().toLowerCase().equalsIgnoreCase("ok"))
                                    System.out.println("Successo richiesta");

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }

                            //Caso createproject
                            else if(operation.toLowerCase().equalsIgnoreCase("createproject")){
                                String result=null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=2)
                                    System.out.println("Passare correttamente i parametri");
                                else
                                    result=createProject(token[1],(String) chiave.attachment()); //nomeProgetto + nomeUtente

                                if(result.equalsIgnoreCase("ok"))
                                    System.out.println("Progetto creato con successo");
                                else result = "Non è possibile creare il progetto";

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }

                            //Caso addmember
                            else if(operation.toLowerCase().equalsIgnoreCase("addmember")){
                                String result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=3)
                                    System.out.println("Passare correttamente i parametri");
                                else
                                    result = addmember(token[1],token[2],(String)chiave.attachment()); //nomeProgetto + nomeUtenteNuovo + nomeUtente
                                if(result.equalsIgnoreCase("ok"))
                                    System.out.println("Successo");
                                else result = "Errore non è possibile aggiungere il membro al progetto";

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }

                            //Caso showmembers
                            else if(operation.toLowerCase().equalsIgnoreCase("showmembers")){
                                ArrayList<String> result = null; //Risposta da inviare al client per indicare i membri di un generico progetto
                                if(token.length!=2)
                                    System.out.println("Passare correttamente i parametri");
                                else
                                    result=showMembers(token[1],(String) chiave.attachment());


                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }

                            //Caso showcards
                            else if(operation.toLowerCase().equalsIgnoreCase("showcards")){
                                Risultati<statoUtente> result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no + Carte presenti nel progetto - Stato
                                if(token.length!=2)
                                    System.out.println("Passare correttamente i parametri");
                                else result =showCards(token[1],(String) chiave.attachment());
                                if(result.getAnswer().equalsIgnoreCase("ok"))
                                    System.out.println("Successo");

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }


                            //caso SHOWCARD
                            else if(operation.toLowerCase().equalsIgnoreCase("showcard")){
                                ArrayList<String> result = new ArrayList<>(); //Risposta per vedere lo stato in cui si trova una determinata carta
                                if(token.length!=3)
                                    System.out.println("Passare correttamente i parametri");
                                else result = showCard(token[1],token[2],(String) chiave.attachment());
                                if(result!=null)
                                    System.out.println("Successo");

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }

                            //caso addCard
                            else if(operation.toLowerCase().equalsIgnoreCase("addcard")){
                                String result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=4)
                                    System.out.println("Passare correttamente i parametri");
                                StringBuilder descrizione = new StringBuilder(); //StringBuilder per memorizzare la descrizione inviata dal client
                                for(int i = 3; i<token.length;i++)
                                    descrizione.append(token[i]).append(" ");
                                result = addCard(token[1],token[2],descrizione.toString(),(String) chiave.attachment());
                                if(result.equalsIgnoreCase("ok"))
                                    System.out.println("Successo");
                                else result = "ERRORE";

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);

                            }

                            //Caso moveCard
                            else if(operation.toLowerCase().equalsIgnoreCase("movecard")){
                                String result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=5)
                                    System.out.println("Passare correttamente i parametri");
                                else result=moveCard(token[1],token[2],token[3],token[4],(String) chiave.attachment()); //Lista partenza e destinazione in maiuscolo
                                if(result.equalsIgnoreCase("ok"))
                                    System.out.println("Successo");

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);



                            }

                            //Caso getCardHisotry
                            else if(operation.toLowerCase().equalsIgnoreCase("getcardhistory")){
                                ArrayList<String> result = null; //Risposta per indicare il ciclo di vita di una carta
                                if(token.length!=3)
                                    System.out.println("Passare correttamente i parametri");
                                else result =getCardHistory(token[1],token[2],(String) chiave.attachment());
                                if(result == null)
                                    System.out.println("Problemi con la getCardHistory");

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);

                            }


                            //Caso cancelProject
                            else if(operation.toLowerCase().equalsIgnoreCase("cancelproject")){
                                String result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=2)
                                    System.out.println("Passare correttamente i parametri");
                                else result  = cancelProject(token[1],(String) chiave.attachment());
                                if(result.equalsIgnoreCase("ok"))
                                    System.out.println("Successo cancellazione progetto");

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);

                            }

                            //Caso readchat
                            else if(operation.toLowerCase().equalsIgnoreCase("readchat")){
                                String result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=2)
                                    System.out.println("Passare correttamente i parameri");
                                else result  = readChat(token[1],(String)chiave.attachment());

                                if(result.equalsIgnoreCase("ok"))
                                    System.out.println("Successo lettura messaggio");


                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }

                            //caso sendChatMsg
                            else if(operation.toLowerCase().equalsIgnoreCase("sendchatmsg")){
                                String result = null; //Risposta da inviare al client per dire se si è avuto successo oppure no
                                if(token.length!=2)
                                    System.out.println("Passare correttamente i parametri");
                                else result = sendChatMsg(token[1],(String) chiave.attachment());
                                if(result.equalsIgnoreCase("ok"))
                                    System.out.println("Successo nel mandare il messaggio");

                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);
                            }

                            //caso Esc
                            else if(operation.toLowerCase().equalsIgnoreCase("esc")){
                                System.out.println("Prova chiusura");
                                this.dataHashMap.remove(client);
                                client.close();
                                chiave.cancel();
                                System.out.println("WORTH: il client:"+ client.socket().getRemoteSocketAddress() + " chiude la connessione");
                            }

                            else{
                                String result = "Passare il comando corretto";
                                datas = this.dataHashMap.get(client);
                                output = new ByteArrayOutputStream();
                                objoutput = new ObjectOutputStream(output);
                                objoutput.writeObject(result);
                                res = output.toByteArray();
                                datas.add(res);

                            }



                            //fine isReadble
                            chiave.interestOps(SelectionKey.OP_WRITE);

                        }

                        //Invio degli esisti delle operazioni al client
                        else if(chiave.isWritable()){
                            SocketChannel client = (SocketChannel) chiave.channel();
                            datas = this.dataHashMap.get(client);
                            Iterator<byte[]> objects = datas.iterator();
                            while(objects.hasNext()){
                                byte[] object = objects.next();
                                objects.remove();
                                client.write(ByteBuffer.wrap(object));
                            }
                            chiave.interestOps(SelectionKey.OP_READ);

                        }


                    } catch (IOException  | CancelledKeyException e) {
                        chiave.cancel();
                        try{
                            chiave.channel().close();
                        } catch (IOException esc){}

                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }




    private risultatiLogin<statoUtente> login(String nomeUtente, String password) throws RemoteException {
        risultatiLogin<statoUtente> result;
        boolean check = false;
        ArrayList<indirizziMulticast> multicast = new ArrayList<>(); //progetti dove il l'utente è membro
        ArrayList<statoUtente> listaUtenti = new ArrayList<>();
        String answer=null;
        //Controlli per vedere se è possibile effettuare il login
        if(nomeUtente.isEmpty() || password.isEmpty())
            answer = "Errore, passare correttamente i parametri";
        else {
            if(utenti.isEmpty())
                answer= "Devi essere prima online";
            else {
                for (Utente user : utenti) {
                    if (user.getNickname().equalsIgnoreCase(nomeUtente))
                        if (user.getPassword().equalsIgnoreCase(password)) {
                            if (user.getStatus().equals("offline")) {
                                check = true;
                                update(nomeUtente, "online"); //Aggiornare tramite RMI la lista degli utenti
                                user.setStatus("online");
                                for (Progetto progetto : progetti) //Invio delle informazioni dei progetti ai quali il client è partecipante
                                    if (progetto.findMembro(nomeUtente))
                                        multicast.add(new indirizziMulticast(progetto.nomeProgetto(), progetto.getIndirizzoMulticast(), progetto.getPorta(), progetto.getMs()));
                            }
                            else answer = "Utente già online";
                        }
                    else answer = "Password errata";
                    listaUtenti.add(new statoUtente(user.getNickname(),user.getStatus()));
                }
                if(!check && answer == null) answer = "Non c'è nessun utente con questo nome";
            }
        }
        if(check){
            result = new risultatiLogin<>("ok",listaUtenti,multicast);
            try{ //Scrittura sul file Utenti per cambiare lo status dell'utente appena loggato
                objMapper.writeValue(userFile,utenti);
            } catch (JsonGenerationException e) {
                e.printStackTrace();
            } catch (JsonMappingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else result = new risultatiLogin<>(answer,null,null);
        return  result;
    }


    private String logout(String nomeUtente) throws RemoteException {
        boolean check = false;
        //Controlli per vedere se è possibile effettuare l'operazione
        if(nomeUtente.isEmpty())
           return "Errore: manca il nome utente";
        if(utenti.isEmpty())
            return "Errore, nessun utente registrato";
        for(Utente user: utenti){

            if(user.getNickname().equalsIgnoreCase(nomeUtente))
                if (user.getStatus().equalsIgnoreCase("online")) {
                    try {
                        update(nomeUtente, "offline"); //Aggiornare tramite RMI la lista degli utenti
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    user.setStatus("offline");
                    try{ //Scrittura sul file Utenti dell'avvenuto logout
                        objMapper.writeValue(userFile,utenti);
                    } catch (JsonGenerationException e) {
                        e.printStackTrace();
                    } catch (JsonMappingException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return "ok";
                }
           else return "L'utente non è online";
        }
        return "L'utente non esiste";
    }

    private String  listusers() {
        //I controlli sono effettuati lato client siccome conosce la lista degli utenti
        if(utenti.isEmpty())
            return "Non ci sono utenti registrati";
        return "ok";
    }

    private String listOnlineUsers() {
        //I controlli sono effettuati lato client siccome conosce la lista degli utenti
        if(utenti.isEmpty())
            return "Non ci sono utenti registrati";
        return "ok";
    }


    private Risultati<String> listProjects(String nickname) {
        //Controlli per verifare se è possibile completare l'operazione
        if(progetti.isEmpty())
            return new Risultati<>("Errore, nessun progetto disponibile",null);
        ArrayList<String> projects = new ArrayList<>(); //Array di stringhe da passare al client contenente tutti i progetti ai quali è mebrro
        for(Progetto progetto: progetti){
            if(progetto.findMembro(nickname))
                projects.add(progetto.nomeProgetto());
        }
        if(projects.isEmpty())
            return new Risultati<>("L'utente non fa parte di nessun progetto",null);
        else return new Risultati<>("ok",projects);
    }



    private String createProject(String nomeProgetto, String nomeUtente) throws IOException {
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeUtente.isEmpty() || nomeProgetto.isEmpty())
            return "Errore nel passaggio dei parametri";
        for(Progetto project: progetti) {
            if (project.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                return "Il progetto da creare esiste già";
        }
        String indirizzoIp = generateIp(224,0,0,0); //generazione di indirizzo ip Multicast
        int port = rand.nextInt(65535-1025+1)+1025; //Generazione randomica della porta
        Progetto progetto = new Progetto(nomeProgetto,nomeUtente,indirizzoIp,port); //creazione nuovo progetto
        progetti.add(progetto);
        try{ //Scrittura su file dei progetti
            objMapper.writeValue(projectFile,progetti);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            refreshMulticast(progetto,nomeUtente); //Aggiornamento multicast
        } catch (Exception e) {
            e.printStackTrace();
        }

        MulticastSocket ms; //Il server joina al nuovo progetto
        try{
            ms = new MulticastSocket();
            ms.joinGroup(InetAddress.getByName(indirizzoIp));
            ms.setSoTimeout(2000);
            multicastSockets.add(new indirizziMulticast(nomeProgetto,indirizzoIp,port,ms));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "ok";
    }



    private String addmember(String nomeProgetto, String nuovoMembro, String utenteAdder) {
        //Controlli per verificare se è possibile completare l'operazione
        boolean findProgetto = false;
        boolean findMembro = false;
        String answer = null;

        for(Utente user : utenti){
            if(user.getNickname().equalsIgnoreCase(nuovoMembro))
                findMembro=true;
        }

        if(findMembro){
            for(Progetto progetto: progetti){
                if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                    if(progetto.findMembro(nuovoMembro))
                        return "Utente già presente";
                }
            }
            for(Progetto progetto: progetti){
                if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                    if(!progetto.findMembro(utenteAdder))
                        return "Errore non fai parte del progetto";
                    answer = progetto.addMember(nuovoMembro); //aggiunta membro al progetto
                    findProgetto = true;
                    if(answer.equalsIgnoreCase("ok")){
                        try{ //Scrittura sul file dei progetti
                            objMapper.writeValue(projectFile,progetti);
                        } catch (JsonGenerationException e) {
                            e.printStackTrace();
                        } catch (JsonMappingException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try{
                            refreshMulticast(progetto,nuovoMembro); //Aggiornamento multicast
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if(!findProgetto) return "Errore, nessun progetto";

            return answer;
        }
        else return "Errore, l'utente non esiste";
    }

    private ArrayList<String> showMembers(String nomeProgetto, String nomeUtente) {
        ArrayList<String> membri = new ArrayList<>();
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || nomeUtente.isEmpty() )
            return null;

        boolean findProgetto = false;

        for(Progetto progetto: progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto=true;
        }

        if(!findProgetto)
            return membri;

        for(Progetto progetto : progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                if(!progetto.findMembro(nomeUtente))
                    return membri;
               membri = progetto.getMembers(); //ArrayList di membri del progetto
            }
        }

        if(membri.isEmpty())
            return membri;
        else return membri;
    }



    private Risultati<statoUtente> showCards(String nomeProgetto, String nomeUtente){
        ArrayList<statoUtente> cards = new ArrayList<>();
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || nomeUtente.isEmpty())
            return new Risultati<>("Errore",null);

        boolean findProgetto = false;

        for(Progetto progetto : progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto=true;
        }

        if(!findProgetto)
            return new Risultati<>("Nessun progetto trovato",null);

        for(Progetto progetto : progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                if(!progetto.findMembro(nomeUtente))
                    return new Risultati<>("Utente non presente",null);
                cards = progetto.getCardsName(); //Lista di carte nel progetto
                if(cards == null)
                    return new Risultati<>("Nessuna carta nel progetto",null);
                else return new Risultati<>("ok",cards);
            }
        }
        return new Risultati<>("Progetto non presente",null);
    }


    private String addCard(String nomeProgetto, String nomeCarta, String descrizione, String nomeUtente) {
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || nomeCarta.isEmpty() || descrizione.isEmpty() || nomeUtente.isEmpty())
            return "ERRORE nel passaggio dei parametri";

        if(progetti.isEmpty())
            return "La lista dei progetti è vuota";

        boolean findProgetto = false;
        String answer;
        for(Progetto progetto : progetti) {
            if (progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto = true;
        }

        if(!findProgetto) return "Progetto non trovato";

        for(Progetto progetto: progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                if(!progetto.findMembro(nomeUtente))
                    return "Utente non presente nei membri del progetto";
                answer= progetto.addCard(nomeCarta,descrizione);
                if(answer.equalsIgnoreCase("ok")) {
                    try { //Scrittura sul file dei progetti
                        objMapper.writeValue(projectFile, progetti);
                    } catch (JsonGenerationException e) {
                        e.printStackTrace();
                    } catch (JsonMappingException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return answer;
            }
        }
        return "Errore";
    }

    private ArrayList<String> showCard(String nomeProgetto, String cardName, String nomeUtente) {
        ArrayList<String> answer = new ArrayList<>();
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || cardName.isEmpty() || nomeUtente.isEmpty())
            return answer = null;
        if(progetti.isEmpty())
            return answer = null;

        boolean findProgetto = false;

        for(Progetto progetto: progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto=true;
        }

        if(!findProgetto)
            return answer = null;

        for(Progetto progetto : progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                if(!progetto.findMembro(nomeUtente))
                    return answer = null;
                ArrayList<String> cardInfo = new ArrayList<>();
                cardInfo = progetto.showCard(cardName);
                if(cardInfo == null)
                    return answer = null;
                return answer = cardInfo;
            }
        }
        return answer = null;
    }


    private String moveCard(String nomeProgetto, String nomeCarta, String listaPartenza, String listaDestinazione, String nomeUtente) {
        String answer = null;
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || nomeCarta.isEmpty() || nomeUtente.isEmpty() || listaPartenza.isEmpty() || listaDestinazione.isEmpty())
            return answer = "Passare correttamente i parametri";
        if(progetti.isEmpty())
            return answer = "Non è presente nessun progetto";
        boolean findProgetto = false;

        for(Progetto progetto: progetti) {
            if (progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto = true;
        }

        if(!findProgetto)
            return answer = "Il progetto cercato non esiste";
        boolean findUtente=false;
        for(Progetto progetto: progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                if(!progetto.findMembro(nomeUtente))
                    return answer = "Utente non è presente nei memberi del progetto";
                findUtente=true;
            }
        }

        if(findUtente){
            for(Progetto progetto : progetti){
                if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                    answer = progetto.moveCard(nomeCarta,listaPartenza,listaDestinazione); //Muovere la carta tra liste
                    if(answer.equalsIgnoreCase("ok")){
                        try{ //Scrittura sul file dei progetti
                            objMapper.writeValue(projectFile,progetti);
                        } catch (JsonGenerationException e) {
                            e.printStackTrace();
                        } catch (JsonMappingException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        //Invio messaggio sulla chat ai membri del progetto
                        String mex = "Card spostata da: " + listaPartenza +" a: " +listaDestinazione;
                        byte [] buffer = mex.getBytes();
                        DatagramPacket datagram;
                        System.out.println("Invio informazione");
                        for(indirizziMulticast sock: multicastSockets){
                            if(sock.getIndirizzoIP().equalsIgnoreCase(progetto.getIndirizzoMulticast())){
                                try{
                                    datagram = new DatagramPacket(buffer,buffer.length,InetAddress.getByName(sock.getIndirizzoIP()),sock.getPorta());
                                    sock.getSocket().send(datagram);
                                } catch (UnknownHostException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                    }
                    return answer;
                }
            }
        }
        return answer;
    }


    private ArrayList<String> getCardHistory(String nomeProgetto, String nomeCarta, String nomeUtente) {
        ArrayList<String> answer = new ArrayList<>();
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || nomeCarta.isEmpty() || nomeUtente.isEmpty() )
            return answer = null;
        if(progetti.isEmpty())
            return answer = null;

        boolean findProgetto = false;

        for(Progetto progetto : progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto = true;
        }

        if(!findProgetto)
            return answer = null;
        for(Progetto progetto: progetti) {
            if (progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)) {
                if (!progetto.findMembro(nomeUtente))
                    return answer = null;
                answer = progetto.getCardHisotry(nomeCarta); //Restuisce un array del ciclo di vita della carta
                if(answer == null)
                    return answer = null;
                return answer;
            }
        }
        return answer;
    }


    private String cancelProject(String nomeProgetto, String nomeUtente){
        String answer = null;
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || nomeUtente.isEmpty())
            return answer = "ERRORE nei parametri";
        if(progetti.isEmpty())
            return answer = "Lista dei progetti vuota";

        boolean findProgetto = false;

        for(Progetto progetto: progetti) {
            if (progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto = true;
        }

        Progetto nuovo = null;

        if(!findProgetto)
            return answer = "Progetto non presente nella lista";

        for(Progetto progetto: progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)) {
                if (!progetto.findMembro(nomeUtente))
                    return answer = "L'utente non fa parte del progetto";
                if (!progetto.isDone())
                    return answer = "Non è possibile cancellare il progetto";
                progetti.remove(progetto); //Rimozione progetto

                try {
                    //Controlli per verificare se è possibile completare l'operazione
                    objMapper.writeValue(projectFile, progetti);
                } catch (JsonGenerationException e) {
                    e.printStackTrace();
                } catch (JsonMappingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                nuovo = progetto;

                //Il server abbandona il progetto
                indirizziMulticast ms = null;

                for (indirizziMulticast sock : multicastSockets) {
                    if (sock.getIndirizzoIP().equalsIgnoreCase(progetto.getIndirizzoMulticast())) {
                        try {
                            sock.getSocket().leaveGroup(InetAddress.getByName(progetto.getIndirizzoMulticast()));
                            ms = sock;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if(ms!=null)
                    multicastSockets.remove(ms);

                try{
                    updateMulticastAll(nuovo);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                return answer= "ok";
            }
        }
        return answer = "ERRORE, progetto non esistente";
    }


    //metodo restituisce solo ok oppure errore poichè il client possiede le informazioni necessarie
    private String readChat(String nomeProgetto, String nomeUtente){
        String answer = null;
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || nomeUtente.isEmpty())
            return answer = "Errore, passare bene i comandi";

        boolean findProgetto = false;

        for(Progetto progetto : progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto=true;
        }

        if(!findProgetto)
            return answer = "Progetto non esistente";

        for(Progetto progetto: progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                if(!progetto.findMembro(nomeUtente))
                    return answer = "Memebro non esistente";
                return answer = "ok";
            }
        }
        return answer = "Progetto non trovato";
    }

    //metodo restituisce solo ok oppure errore poichè il client possiede le informazioni necessarie
    private String sendChatMsg(String nomeProgetto, String nomeUtente) {
        String answer = null;
        //Controlli per verificare se è possibile completare l'operazione
        if(nomeProgetto.isEmpty() || nomeUtente.isEmpty())
            return answer = "Passare correttamente i parametri";

        boolean findProgetto = false;

        for(Progetto progetto : progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto))
                findProgetto=true;
        }


        if(!findProgetto)
            return answer = "Progetto non eistente";

        for(Progetto progetto: progetti){
            if(progetto.nomeProgetto().equalsIgnoreCase(nomeProgetto)){
                if(!progetto.findMembro(nomeUtente))
                    return answer = "Membro non eistente";
                return answer = "ok";
            }
        }
        return answer = "Progetto non trovato";
    }



    @Override
    //Metodo RMI per la registrazione di un nuovo utente
    public String registrazione(String nomeUtente, String password) throws RemoteException {
        System.out.println("Registrazione nuovo utente: UTENTE: "+nomeUtente + " PASSWORD: " + password);
        if(nomeUtente == null || password == null)
            return "ERRORE nel passaggio dei paremetri";
        System.out.println("Verifica delle credienziali utente");
        for(Utente utente: utenti)
            if(utente.getNickname().equalsIgnoreCase(nomeUtente))
                return "L'utente è già registrato";
        System.out.println("Creazione nuovo utente ");

        update(nomeUtente,"offline");
        System.out.println("L'utente prima di poter utilizzare le funzionalità del server deve loggare");
        utenti.add(new Utente(nomeUtente,password));
        try{
            objMapper.writeValue(userFile,utenti);
        } catch (JsonGenerationException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Utente: "+ nomeUtente + " registrato con successo";
    }





    @Override
    //Metodo per aggiornare tutti i client dell'avvenuta registrazione di un nuovo client
    public void registerForCallback(RMIEventInterface ClientInterface, String nickUtente) throws RemoteException {
        boolean contains = clients.stream()
                .anyMatch(client -> ClientInterface.equals(client.getClient()));
        if (!contains){
            clients.add(new CallBackInfo(ClientInterface,nickUtente));
            System.out.println("CALLBACK SYSTEM: nuovo cliente registrato." );
        }

    }

    @Override
    //Metodo per aggiornare tutti i client dell'uscita di un client
    public void unregisterForCallback(RMIEventInterface ClientInterface) throws RemoteException {
        CallBackInfo user = clients.stream()
                .filter(client -> ClientInterface.equals(client.getClient()))
                .findAny()
                .orElse(null);
        if (user!=null) {
            clients.remove(user);
            System.out.println("CALLBACK SYSTEM: Client unregistered");
        }
        else System.out.println("CALLBACK SYSTEM: Unable to unregister client.");

    }

    //Aggiornamento lista degli utenti
    private void update(String nomeUtente, String status) throws RemoteException {
        doCallbacks(nomeUtente,status);
    }

    //Aggiornamento dello stato di un generico utente a tutti gli utenti di WORTH
    private void doCallbacks(String nomeUtente, String status) throws RemoteException {
        LinkedList<RMIEventInterface> errors = new LinkedList<>();
        System.out.println("CALLBACK SYSTEM: Starting callbacks.");
        for (CallBackInfo callbackinfoUser : clients) {
            RMIEventInterface client = callbackinfoUser.getClient();
            try {
                client.notifyStatus(nomeUtente, status);
            } catch (RemoteException e) {
                errors.add(client);
            }
        }
        if(!errors.isEmpty()) {
            System.out.println("CALLBACK SYSTEM: Unregistering clients that caused an error!");
            for(RMIEventInterface Nei : errors)
                unregisterForCallback(Nei);
        }
        System.out.println("CALLBACK SYSTEM: Callbacks complete.");
    }

    //Aggiornare gli utenti sui progetti
    private void refreshMulticast(Progetto progetto, String nomeUtente) throws RemoteException {
        doChatCallBacks(progetto,nomeUtente);
    }

    //Aggiornare gli utenti sui progetti per l'aggiunta di un nuovo membro
    private synchronized void doChatCallBacks(Progetto progetto, String nomeUtente) throws RemoteException{
        System.out.println("CALLBACK SYSTEM: Starting callbacks.");
        for (CallBackInfo callbackinfoUser : clients) {
            RMIEventInterface client = callbackinfoUser.getClient();
            if (callbackinfoUser.getNomeClient().equalsIgnoreCase(nomeUtente))
                client.notifyChat(progetto.nomeProgetto(),progetto.getIndirizzoMulticast(),progetto.getPorta());
        }
        System.out.println("CALLBACK SYSTEM: Callbacks complete.");
    }




    //Aggiornare gli utenti sui progetti
    private void updateMulticastAll(Progetto nomeProgetto)throws RemoteException {
        doChatCallBacksALL(nomeProgetto);
    }

    //Aggiornare tutti gli utenti della cancellazione del progetto
    private synchronized void doChatCallBacksALL(Progetto nomeProgetto) throws RemoteException {
        LinkedList<RMIEventInterface> errors = new LinkedList<>();
        System.out.println("CALLBACK SYSTEM: Starting callbacks.");
        for (CallBackInfo callbackinfoUser : clients) {
            RMIEventInterface client = callbackinfoUser.getClient();
            try {
                if (nomeProgetto.findMembro(callbackinfoUser.getNomeClient()))
                    client.notifyProgetto(nomeProgetto.getIndirizzoMulticast(),nomeProgetto.getPorta());
            } catch (RemoteException e) {
                errors.add(client);
            }
        }
        if(!errors.isEmpty()) {
            System.out.println("CALLBACK SYSTEM: Unregistering clients that caused an error!");
            for(RMIEventInterface Nei : errors) unregisterForCallback(Nei);
        }
        System.out.println("CALLBACK SYSTEM: Callbacks complete.");
    }


    //Metodo utilizzato per generare indirizzi IP in maniera randomica
    public String generateIp(int blocco1, int blocco2, int blocco3, int blocco4){
        Random rd  = new Random();

        blocco1 = rd.nextInt(15)+224;

        blocco2 = (int) (Math.random() * 256);
        blocco3 = (int) (Math.random() * 256);
        blocco4 = (int) (Math.random() * 256);

        return blocco1 + "." + blocco2 + "." + blocco3 + "." + blocco4;
    }


    public static void main(String [] args) throws IOException {
        ServerMain server = new ServerMain();
        try{
            ServerMainInterfaceRMI stub = (ServerMainInterfaceRMI) UnicastRemoteObject.exportObject(server,0);
            LocateRegistry.createRegistry(RMIport);
            Registry registry= LocateRegistry.getRegistry(RMIport);
            registry.rebind("ServerRMI",stub);

        } catch (RemoteException e) {
            e.printStackTrace();
        }
        server.start();
    }



}
