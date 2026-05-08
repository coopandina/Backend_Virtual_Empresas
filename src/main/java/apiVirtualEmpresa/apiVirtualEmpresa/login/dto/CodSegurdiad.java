package apiVirtualEmpresa.apiVirtualEmpresa.login.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor


public class CodSegurdiad {

    private String ipterminal;

    private String Codaccess_codigo_temporal;

    public String getCodaccess_codigo_temporal() {
        return Codaccess_codigo_temporal;
    }



    public void setCodaccess_codigo_temporal(String codaccess_codigo_temporal) {
        Codaccess_codigo_temporal = codaccess_codigo_temporal;
    }

    public String getIpterminal() {
        return ipterminal;
    }

    public void setIpterminal(String ipterminal) {
        this.ipterminal = ipterminal;
    }

    @Override
    public String toString() {
        return "CodSegurdiad{" +
                "Codaccess_codigo_temporal='" + Codaccess_codigo_temporal + '\'' +
                '}';
    }
}
