package apiVirtualEmpresa.apiVirtualEmpresa.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DashboardUtils {

    private String codCta;
    private String idTerClien;

    private String passwordAct;
    private String passwordNuev;
    private String passwordConf;
    private String codTemp;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaInicio;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaFin;

    public String getIdTerClien() {
        return idTerClien;
    }

    public String getCodCta() {
        return codCta;
    }

    public void setIdTerClien(String idTerClien) {
        this.idTerClien = idTerClien;
    }

    public void setCodCta(String codCta) {
        this.codCta = codCta;
    }

    @Override
    public String toString() {
        return "DashboardUtils{" +
                "codCta='" + codCta + '\'' +
                '}';
    }
}
