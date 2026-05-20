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
                // [kguanoluisa] - Buscar el ( desde la posición de "The specified table" para evitar
                // capturar el ( del CALL SQL que aparece antes en el mensaje de Hibernate - 20/05/2026
                int markerIdx = sqlError.indexOf("The specified table");
                int start = sqlError.indexOf("(", markerIdx);
                int end = sqlError.indexOf(")", start);
                if (start != -1 && end != -1 && end > start) {
                    String tableName = sqlError.substring(start + 1, end);
                    response.put("message", "La tabla especificada (" + tableName + ") no existe en la base de datos.");
                } else {
                    response.put("message", "La tabla especificada no existe en la base de datos.");
                }
                response.put("status", "ERROR_TABLA_INEXISTENTE");
            } else if (sqlError.contains("Unique constraint") && sqlError.contains("violated")) {
                // [kguanoluisa] - Extrae el nombre del procedimiento almacenado del mensaje JDBC de Hibernate.
                // Formato: "JDBC exception executing SQL [CALL cnxprc_reg_spi01_wb(...)] [Unique constraint (...) violated.]"
                // - 20/05/2026
                String procName = extractCallName(sqlError);
                if (procName != null) {
                    response.put("message", "Registro duplicado en el procedimiento (" + procName + "): ya existe un registro con esos datos en la base de datos.");
                } else {
                    response.put("message", "Registro duplicado: ya existe un registro con esos datos en la base de datos.");
                }
                response.put("status", "ERROR_REGISTRO_DUPLICADO");
            } else if (sqlError.contains("not found in any table") || sqlError.contains("Column (")) {
                int markerIdx = sqlError.contains("Column (") ? sqlError.indexOf("Column (") : 0;
                int start = sqlError.indexOf("(", markerIdx);
                int end = sqlError.indexOf(")", start);
                if (start != -1 && end != -1 && end > start) {
                    String columnName = sqlError.substring(start + 1, end);
                    response.put("message", "La columna especificada (" + columnName + ") no existe en la tabla de la base de datos.");
                } else {
                    response.put("message", "La columna especificada no existe en la tabla de la base de datos.");
                }
                response.put("status", "ERROR_COLUMNA_INEXISTENTE");
            } else {
                response.put("message", "Error de base de datos: " + sqlError);
                response.put("status", "ERROR_BASE_DATOS");
            }
        }
        // 2. Captura excepciones silenciosas específicas de fechas (función pkmprdr)
        else if (ex.getMessage() != null && ex.getMessage().contains("pkmprdr")) {
            response.put("message", "Error de consistencia o incompatibilidad de fechas en la base de datos.");
            response.put("status", "ERROR_FECHAS_BD");
        }
        // 3. Captura transacciones revertidas inesperadamente de forma genérica
        else if (ex.toString().contains("UnexpectedRollbackException")) {
            response.put("message", "La transacción fue revertida inesperadamente en el servidor de base de datos.");
            response.put("status", "ERROR_TRANSACCION_REVERTIDA");
        }
        // 4. Otros errores
        else {
            response.put("message", "Error interno del servidor");
            response.put("status", "ERROR_DESCONOCIDO");
        }

        response.put("success", false);
        response.put("error", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // [kguanoluisa] - Extrae el nombre del procedimiento del mensaje JDBC de Hibernate.
    // Formato entrada: "JDBC exception executing SQL [CALL nombreProcedimiento(...)] [error...]"
    // Formato salida: "nombreProcedimiento"
    // - 20/05/2026
    private String extractCallName(String msg) {
        if (msg == null) return null;
        int callIdx = msg.indexOf("CALL ");
        if (callIdx == -1) return null;
        int parenIdx = msg.indexOf("(", callIdx);
        if (parenIdx == -1) return null;
        return msg.substring(callIdx + 5, parenIdx).trim();
    }

    private String getRootSqlErrorMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException) {
                return cause.getMessage();
            }
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("is not in the database") ||
                                msg.contains("The specified table") ||
                                msg.contains("Unique constraint") ||
                                msg.contains("SQL Error:") ||
                                msg.contains("table (") ||
                                msg.contains("not found in any table") ||
                                msg.contains("Column (") ||
                                msg.contains("andctrlvirlogin"))) {
                return msg;
            }
            cause = cause.getCause();
        }
        return null;
    }
}
