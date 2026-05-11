package apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.Controller;
import apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.Service.TransfDirectService;
import apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.dto.TransfDirectUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transfDirect")


public class TransfDirectController {

    private final TransfDirectService transfDirectService;

    @PostMapping("/codTempDirectas")
    public ResponseEntity<Map<String, Object>>codTempDirectas(HttpServletRequest request, Authentication authentication, @RequestBody TransfDirectUtils dto) {
        return transfDirectService.genCodDirectas(request, authentication, dto);
    }

    @PostMapping("/srtGrabarDirectas")
    public ResponseEntity<Map<String, Object>>srtGrabarDir(HttpServletRequest request, Authentication authentication, @RequestBody TransfDirectUtils dto) {
        return transfDirectService.srtGrabarDir(request,authentication, dto);
    }

    // [kguanoluisa] - Se agrega ExceptionHandler para capturar errores de rollback por fechas BD o restricciones y retornar mensaje controlado - 11/05/2026
    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleControllerExceptions(Exception ex) {
        Map<String, Object> response = new java.util.HashMap<>();

        // Verifica si es el error de Rollback o de Informix para arrojar el error específico solicitado
        if (ex.toString().contains("UnexpectedRollbackException") || 
            (ex.getMessage() != null && ex.getMessage().contains("pkmprdr"))) {
            response.put("message", "Error fechas de base de datos diferentes");
            response.put("status", "ERROR_FECHAS_BD");
        } else {
            response.put("message", "Error interno del servidor");
            response.put("status", "ERROR_DESCONOCIDO");
        }

        response.put("success", false);
        response.put("error", ex.getMessage());
        return new ResponseEntity<>(response, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}