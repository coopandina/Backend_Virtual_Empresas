package apiVirtualEmpresa.apiVirtualEmpresa.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

// [kguanoluisa] - Manejador global para capturar errores inesperados o de base de datos y unificar respuesta - 11/05/2026
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        Map<String, Object> response = new HashMap<>();

        // Captura excepciones silenciosas de rollback por fallos transaccionales de BD analizados
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

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
