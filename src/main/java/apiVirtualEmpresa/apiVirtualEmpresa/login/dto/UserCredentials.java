package apiVirtualEmpresa.apiVirtualEmpresa.login.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class UserCredentials {
    private String ClienIdeClien;
    private  String UsvcoIdeUsv;
    private String UsvcoPswd;
    private  String ipterminal;

    public String getClienIdeClien() {
        return ClienIdeClien;
    }

    public void setClienIdeClien(String clienIdeClien) {
        ClienIdeClien = clienIdeClien;
    }

    public String getUsvcoIdeUsv() {
        return UsvcoIdeUsv;
    }

    public void setUsvcoIdeUsv(String usvcoIdeUsv) {
        UsvcoIdeUsv = usvcoIdeUsv;
    }

    public String getUsvcoPswd() {
        return UsvcoPswd;
    }

    public void setUsvcoPswd(String usvcoPswd) {
        UsvcoPswd = usvcoPswd;
    }

    public String getIpterminal() {
        return ipterminal;
    }
}
