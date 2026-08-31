package apiVirtualEmpresa.apiVirtualEmpresa.nominas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class NominasUtils {

    private String valservi;
    private String tipestado;
    private String ctadp;
    private String estado;
    private String numnomina;
    private String codreg;
    private String descripcion;
    private String ideClien;
    private String ctaDestino;
    private String ctaOrigen;
    private String monto;
    private String codbanco;
    private String plexaCodEtcptec;
    private String plexaTipTrans;
    private String plexaTlfDesti;
    private String codTempDirec;
    private String codTempExter;
    private String nombresDes;
    private String nombresBenef;
    private String cedulaBenef;
    private Integer tipoCuenta;
    private BigDecimal valTransfer;
    private String ipterminal;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaInicio;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaFin;


}
