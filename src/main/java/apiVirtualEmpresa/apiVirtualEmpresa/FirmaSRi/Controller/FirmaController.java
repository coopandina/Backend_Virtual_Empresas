package apiVirtualEmpresa.apiVirtualEmpresa.FirmaSRi.Controller;

import apiVirtualEmpresa.apiVirtualEmpresa.FirmaSRi.Service.FirmaService;
import apiVirtualEmpresa.apiVirtualEmpresa.FirmaSRi.dto.FirmaUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/firma-sri")
public class FirmaController {


    private final FirmaService firmaService;

    public FirmaController(FirmaService firmaService) {
        this.firmaService = firmaService;
    }

    @PostMapping("/firmar")
    public ResponseEntity<Map<String, Object>> firmarXml(@RequestBody FirmaUtils request) {
        return firmaService.firmarArchivo(request);
    }


    @PostMapping("/grabrardoc")
    public ResponseEntity<Map<String, Object>> grabarDocumento(@RequestBody FirmaUtils request) {
        firmaService.grabarDocumento(request);
        return ResponseEntity.ok(Map.of("status", true, "mensaje", "Proceso ejecutado correctamente")
        );
    }


    @PostMapping("/procesar")
    public ResponseEntity<Map<String, Object>> srtProcesa(@RequestBody FirmaUtils request) {
        Map<String, Object> resultado = firmaService.srtProcesa(request);
        return ResponseEntity.ok(resultado);
    }
}
