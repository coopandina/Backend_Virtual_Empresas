package apiVirtualEmpresa.apiVirtualEmpresa.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para toda la aplicación.
 *
 * Utiliza @ControllerAdvice para interceptar automáticamente cualquier excepción
 * que no haya sido capturada dentro de un controlador o servicio (es decir, que haya
 * propagado hacia arriba sin un catch propio). Spring la enruta aquí antes de devolver
 * la respuesta HTTP al cliente, garantizando un formato de error uniforme en todos los endpoints.
 *
 * Flujo de clasificación de errores:
 *   1. Recorre la cadena de causas de la excepción buscando el mensaje SQL raíz (getRootSqlErrorMessage).
 *   2. Si encuentra un error SQL, lo clasifica por tipo (classifySqlError):
 *        - ERROR_TABLA_INEXISTENTE  : tabla no existe en la BD (SQL -206).
 *        - ERROR_REGISTRO_DUPLICADO : violación de constraint unique (SQL -268).
 *        - ERROR_COLUMNA_INEXISTENTE: columna no encontrada en ninguna tabla.
 *        - ERROR_BASE_DATOS         : cualquier otro error SQL no clasificado.
 *   3. Si no hay error SQL, evalúa casos especiales:
 *        - ERROR_FECHAS_BD           : incompatibilidad de fechas (función pkmprdr).
 *        - ERROR_TRANSACCION_REVERTIDA: rollback inesperado de Spring.
 *        - ERROR_DESCONOCIDO         : cualquier otro error no identificado.
 *
 * Todos los errores retornan HTTP 500 con el formato JSON:
 *   { "success": false, "status": "...", "message": "...", "error": "..." }
 *
 * @author kguanoluisa
 * @since 11/05/2026
 */
// [kguanoluisa] - Manejador global para capturar errores inesperados o de base de datos y unificar respuesta - 11/05/2026
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        String sqlError = getRootSqlErrorMessage(ex);

        if (sqlError != null) {
            classifySqlError(sqlError, response);
        } else if (ex.getMessage() != null && ex.getMessage().contains("pkmprdr")) {
            setError(response, "Error de consistencia o incompatibilidad de fechas en la base de datos.", "ERROR_FECHAS_BD");
        } else if (ex.toString().contains("UnexpectedRollbackException")) {
            setError(response, "La transacción fue revertida inesperadamente en el servidor de base de datos.", "ERROR_TRANSACCION_REVERTIDA");
        } else {
            setError(response, "Error interno del servidor", "ERROR_DESCONOCIDO");
        }

        response.put("success", false);
        response.put("error", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // [kguanoluisa] - Clasifica el tipo de error SQL y construye el mensaje apropiado - 20/05/2026
    private void classifySqlError(String sqlError, Map<String, Object> response) {
        if (sqlError.contains("The specified table") && sqlError.contains("is not in the database")) {
            String tableName = extractNameAfterMarker(sqlError, "The specified table");
            String msg = tableName != null
                    ? "La tabla especificada (" + tableName + ") no existe en la base de datos."
                    : "La tabla especificada no existe en la base de datos.";
            setError(response, msg, "ERROR_TABLA_INEXISTENTE");

        } else if (sqlError.contains("Unique constraint") && sqlError.contains("violated")) {
            String procName = extractCallName(sqlError);
            String msg = procName != null
                    ? "Registro duplicado en el procedimiento (" + procName + "): ya existe un registro con esos datos en la base de datos."
                    : "Registro duplicado: ya existe un registro con esos datos en la base de datos.";
            setError(response, msg, "ERROR_REGISTRO_DUPLICADO");

        } else if (sqlError.contains("not found in any table") || sqlError.contains("Column (")) {
            String marker = sqlError.contains("Column (") ? "Column (" : null;
            String colName = extractNameAfterMarker(sqlError, marker);
            String msg = colName != null
                    ? "La columna especificada (" + colName + ") no existe en la tabla de la base de datos."
                    : "La columna especificada no existe en la tabla de la base de datos.";
            setError(response, msg, "ERROR_COLUMNA_INEXISTENTE");

        } else {
            setError(response, "Error de base de datos: " + sqlError, "ERROR_BASE_DATOS");
        }
    }

    // [kguanoluisa] - Asigna message y status al mapa de respuesta - 20/05/2026
    private void setError(Map<String, Object> response, String message, String status) {
        response.put("message", message);
        response.put("status", status);
    }

    // [kguanoluisa] - Extrae el texto entre paréntesis que sigue al marcador indicado dentro del mensaje.
    // Si marker es null, busca desde el inicio. Ejemplo:
    //   msg="The specified table (cnxspi01) is not...", marker="The specified table" → "cnxspi01"
    // - 20/05/2026
    private String extractNameAfterMarker(String msg, String marker) {
        if (msg == null) return null;
        int fromIdx = (marker != null) ? msg.indexOf(marker) : 0;
        if (fromIdx == -1) return null;
        int start = msg.indexOf("(", fromIdx);
        int end = (start != -1) ? msg.indexOf(")", start) : -1;
        return (start != -1 && end > start) ? msg.substring(start + 1, end) : null;
    }

    // [kguanoluisa] - Extrae el nombre del procedimiento del mensaje JDBC de Hibernate.
    // Formato: "JDBC exception executing SQL [CALL nombreProcedimiento(...)] [error...]"
    // - 20/05/2026
    private String extractCallName(String msg) {
        if (msg == null) return null;
        int callIdx = msg.indexOf("CALL ");
        if (callIdx == -1) return null;
        int parenIdx = msg.indexOf("(", callIdx);
        if (parenIdx == -1) return null;
        return msg.substring(callIdx + 5, parenIdx).trim();
    }

    // [kguanoluisa] - Recorre la cadena de causas de la excepción buscando el mensaje SQL raíz - 18/05/2026
    private String getRootSqlErrorMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException) {
                return cause.getMessage();
            }
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("is not in the database") ||
                                msg.contains("The specified table")    ||
                                msg.contains("Unique constraint")      ||
                                msg.contains("SQL Error:")             ||
                                msg.contains("table (")                ||
                                msg.contains("not found in any table") ||
                                msg.contains("Column (")               ||
                                msg.contains("andctrlvirlogin"))) {
                return msg;
            }
            cause = cause.getCause();
        }
        return null;
    }
}
