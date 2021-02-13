public class CallBackInfo {
   private RMIEventInterface client;
   private String nomeClient;

   public CallBackInfo(RMIEventInterface client, String nomeClient){ //Classe utilizzata per ricavare informazioni per le CallBacks
        this.client = client;
        this.nomeClient=nomeClient;
   }
   public RMIEventInterface getClient(){
       return this.client;
   }

   public String getNomeClient(){
       return this.nomeClient;
   }
}
