package apiVirtualEmpresa.apiVirtualEmpresa.Password.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Data
public class PasswordUtils {
    private String codCta;
    private String idTerClien;
    private String passwordAct;
    private String passwordNuev;
    private String passwordConf;
    private String codTemp;

}
