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

        // [kguanoluisa] - Extracción y priorización dinámica de errores de BD/tabla inexistente (sqlError) para evitar enmascaramientos por rollback - 18/05/2026
        String sqlError = getRootSqlErrorMessage(ex);

        // 1. Prioridad a errores específicos de base de datos (incluso si están envueltos en Rollback)
        if (sqlError != null) {
            if (sqlError.contains("The specified table") && sqlError.contains("is not in the database")) {
                int start = sqlError.indexOf("(");
                int end = sqlError.indexOf(")");
                if (start != -1 && end != -1 && end > start) {
                    String tableName = sqlError.substring(start + 1, end);
                    response.put("message", "La tabla especificada (" + tableName + ") no existe en la base de datos.");
                } else {
                    response.put("message", "La tabla especificada no existe en la base de datos.");
                }
                response.put("status", "ERROR_TABLA_INEXISTENTE");
            } else if (sqlError.contains("andctrlvirlogin")) {
                response.put("message", "La tabla especificada (andctrlvirlogin) no existe en la base de datos.");
                response.put("status", "ERROR_TABLA_INEXISTENTE");
            } else {
                response.put("message", "Error de base de datos: " + sqlError);
                response.put("status", "ERROR_BASE_DATOS");
            }
        } 
        // 2. Captura excepciones silenciosas de rollback genéricas
        else if (ex.toString().contains("UnexpectedRollbackException") || 
            (ex.getMessage() != null && ex.getMessage().contains("pkmprdr"))) {
            response.put("message", "Error fechas de base de datos diferentes o tabla faltante");
            response.put("status", "ERROR_FECHAS_BD");
        } 
        // 3. Otros errores
        else {
            response.put("message", "Error interno del servidor");
            response.put("status", "ERROR_DESCONOCIDO");
        }

        response.put("success", false);
        response.put("error", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String getRootSqlErrorMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException) {
                return cause.getMessage();
            }
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("is not in the database") || 
                                msg.contains("SQL Error:") || 
                                msg.contains("table (") || 
                                msg.contains("andctrlvirlogin"))) {
                return msg;
            }
            cause = cause.getCause();
        }
        return null;
    }
}
