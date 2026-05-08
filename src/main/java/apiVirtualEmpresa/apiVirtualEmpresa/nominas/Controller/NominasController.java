package apiVirtualEmpresa.apiVirtualEmpresa.nominas.Controller;

import apiVirtualEmpresa.apiVirtualEmpresa.nominas.Service.NominasService;
import apiVirtualEmpresa.apiVirtualEmpresa.nominas.dto.NominasUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nominas")
@RequiredArgsConstructor
public class NominasController {

    private final NominasService nominasService;

    @PostMapping("/nominaInternaDatos")
    public ResponseEntity<Map<String, Object>> listDatosNominaInterna(HttpServletRequest request, Authentication authentication, @RequestBody List<NominasUtils> requestDataList) {
        return nominasService.listarDatosNominaInterna(request, authentication, requestDataList);
    }

    // Listar numero de nominas
    @PostMapping("/numNomina")
    public ResponseEntity<Map<String, Object>> numNomina(HttpServletRequest request, Authentication authentication, @RequestBody NominasUtils requestData) {
        return nominasService.numNomina(request, authentication, requestData);
    }
    //listar nominas por estados Y FECHAS
    @PostMapping("/listarNomina")
    public ResponseEntity<Map<String, Object>> listarNomina(HttpServletRequest request, Authentication authentication, @RequestBody NominasUtils requestData) {
        return nominasService.listarNomina(request, authentication, requestData);
    }

    @PostMapping("/listarEstadoNomina")
    public ResponseEntity<Map<String, Object>> listarEstadoNomina(HttpServletRequest request, Authentication authentication, @RequestBody NominasUtils requestData) {
        return nominasService.listarEstadoNomina(request, authentication, requestData);
    }


    //cargar nominas internas cuentas directas
    @PostMapping ("/cargNomInterna")
    public ResponseEntity<Map<String, Object>> cargaNominaInterna(HttpServletRequest request,Authentication authentication, @RequestBody List<NominasUtils> requestDataList) {
        return nominasService.cargaNominaInterna(request, authentication, requestDataList);
    }
    //ACREDITAR VALORES A CUENTAS DIRECTAS
    @PostMapping ("/acreNomInterna")
    public ResponseEntity<Map<String, Object>> acreditarNominaInterna(HttpServletRequest request,Authentication authentication, @RequestBody List<NominasUtils> requestDataList) {
        return nominasService.acreditarNominaInterna(request, authentication, requestDataList);
    }

    //listar nominas para acreditar
    @PostMapping("/listarNominAcre")
    public ResponseEntity<Map<String, Object>> listarNominaAcreditar(HttpServletRequest request, Authentication authentication, @RequestBody NominasUtils requestData) {
        return nominasService.listarNominaAcreditar(request, authentication, requestData);
    }
    //codigo de temporal de transacciones directas

    @PostMapping("/codTempNomInterna")
    public ResponseEntity<Map<String, Object>>genCodNomInterna(HttpServletRequest request, Authentication authentication, @RequestBody NominasUtils requestData) {
        return nominasService.genCodNomInterna(request, authentication, requestData);
    }
    //cargar nominas externas
    @PostMapping("/cargNomExterna")
    public ResponseEntity<Map<String, Object>> cargaNominaExterna(HttpServletRequest request,Authentication authentication, @RequestBody List<NominasUtils> requestDataList) {
        return nominasService.cargaNominaExterna(request, authentication, requestDataList);
    }

    //acreditar nominas externas
    @PostMapping("/acreNomExterna")
     public  ResponseEntity<Map<String, Object>> acreditarNominaExterna(HttpServletRequest request, Authentication authentication, @RequestBody List<NominasUtils> requestDataList){
        return  nominasService.acreditarNominaExterna(request, authentication, requestDataList);
    }

    //generar codigo para nominas externas
    @PostMapping("/codTempNomExterna")
    public ResponseEntity<Map<String, Object>>genCodNomExterna(HttpServletRequest request, Authentication authentication, @RequestBody NominasUtils requestData) {
        return nominasService.genCodNomExterna(request, authentication, requestData);
    }

}
