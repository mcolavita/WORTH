import java.util.*;
public class Utente {
    private String nickname;
    private String password;
    private String status;

    public Utente(String nickname, String password){
        this.nickname= nickname; //NomeUtente
        this.password=password; //Password utente
        this.status="offline"; // stato utente
    }

    public Utente(){

    }

    /**
     *
     * Metodo per avere informazioni sull'utente
     */

    public String getNickname(){
        return this.nickname;
    }
    public String getPassword(){
        return this.password;
    }
    public String getStatus(){
        return this.status;
    }

    public void setStatus(String status) { this.status = status; }

    public void setNickname(String nickname){
        this.nickname = nickname;
    }

    public void setPassword(String password){
        this.password = password;
    }
}
