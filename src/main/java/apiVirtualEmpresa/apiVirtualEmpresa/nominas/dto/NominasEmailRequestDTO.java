package apiVirtualEmpresa.apiVirtualEmpresa.nominas.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NominasEmailRequestDTO {
    private String asunto;
    private String pdfBase64;
    private String nombreArchivo;
    private String tipoEvento; // "CARGA" o "ACREDITACION"
}
