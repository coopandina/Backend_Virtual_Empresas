package apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class TransfDirectUtils {
    private String ctaEnvio;
    private String ctaDestino;
    private String codTempTransDirec;
    private String txtdettrnsf;
    private Float valtrans;


    //PARA TRANSFERENCIAS INTERBANCARIAS
    private String tipCodInsti;
    private String nombresBeneficiario;
    private String cedulaBeneficiario;
    private Integer tipoctabce;
    private String saldoDisponible;
    private String ipterminal;

    public Integer getTipoctabce() {
        return tipoctabce;
    }

    public void setTipoctabce(Integer tipoctabce) {
        this.tipoctabce = tipoctabce;
    }

    public String getTipCodInsti() {
        return tipCodInsti;
    }

    public void setTipCodInsti(String tipCodInsti) {
        this.tipCodInsti = tipCodInsti;
    }

    public String getNombresBeneficiario() {
        return nombresBeneficiario;
    }

    public void setNombresBeneficiario(String nombresBeneficiario) {
        this.nombresBeneficiario = nombresBeneficiario;
    }

    public String getCedulaBeneficiario() {
        return cedulaBeneficiario;
    }

    public void setCedulaBeneficiario(String cedulaBeneficiario) {
        this.cedulaBeneficiario = cedulaBeneficiario;
    }

    public String getCtaEnvio() {
        return ctaEnvio;
    }

    public void setCtaEnvio(String ctaEnvio) {
        this.ctaEnvio = ctaEnvio;
    }

    public String getCtaDestino() {
        return ctaDestino;
    }

    public void setCtaDestino(String ctaDestino) {
        this.ctaDestino = ctaDestino;
    }

    public String getCodTempTransDirec() {
        return codTempTransDirec;
    }

    public void setCodTempTransDirec(String codTempTransDirec) {
        this.codTempTransDirec = codTempTransDirec;
    }

    public String getTxtdettrnsf() {
        return txtdettrnsf;
    }

    public void setTxtdettrnsf(String txtdettrnsf) {
        this.txtdettrnsf = txtdettrnsf;
    }

    public Float getValtrans() {
        return valtrans;
    }

    public void setValtrans(Float valtrans) {
        this.valtrans = valtrans;
    }
}
