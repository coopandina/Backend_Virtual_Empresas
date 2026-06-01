package apiVirtualEmpresa.apiVirtualEmpresa.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para toda la aplicación.
 * <p>
 * Utiliza @ControllerAdvice para interceptar automáticamente cualquier excepción
 * que no haya sido capturada dentro de un controlador o servicio (es decir, que haya
 * propagado hacia arriba sin un catch propio). Spring la enruta aquí antes de devolver
 * la respuesta HTTP al cliente, garantizando un formato de error uniforme en todos los endpoints.
 * <p>
 * Flujo de clasificación de errores:
 * 1. Recorre la cadena de causas buscando el error SQL raíz (getRootSqlError).
 * Retorna el código numérico Informix y el mensaje del error.
 * 2. Clasifica por código numérico primero (más confiable), texto como respaldo:
 * - ERROR_TABLA_INEXISTENTE  : código -206 / tabla no existe en la BD.
 * - ERROR_COLUMNA_INEXISTENTE: código -217 / columna no encontrada.
 * - ERROR_REGISTRO_DUPLICADO : código -268 / violación de constraint unique.
 * - ERROR_BASE_DATOS         : cualquier otro error SQL no clasificado.
 * 3. Si no hay error SQL, evalúa casos especiales:
 * - ERROR_FECHAS_BD           : incompatibilidad de fechas (función pkmprdr).
 * - ERROR_TRANSACCION_REVERTIDA: rollback inesperado de Spring.
 * - ERROR_DESCONOCIDO         : cualquier otro error no identificado.
 * <p>
 * Todos los errores retornan HTTP 500 con el formato JSON:
 * { "success": false, "status": "...", "message": "...", "error": "..." }
 *
 * @author kguanoluisa
 * @since 11/05/2026
 */
// [kguanoluisa] - Manejador global para capturar errores inesperados o de base de datos y unificar respuesta - 11/05/2026
@ControllerAdvice
public class GlobalExceptionHandler {

    // Códigos de error SQL de Informix manejados
    private static final int SQL_ERR_TABLA_INEXISTENTE = -206;
    private static final int SQL_ERR_COLUMNA_INEXISTENTE = -217;
    private static final int SQL_ERR_REGISTRO_DUPLICADO = -268;
    private static final int SQL_ERR_ROUTINE_INEXISTENTE = -674;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        SqlErrorInfo sqlInfo = getRootSqlError(ex);

        if (sqlInfo != null) {
            classifySqlError(sqlInfo, response);
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

    // [kguanoluisa] - Contenedor interno: código numérico SQL + mensaje - 21/05/2026
    private static class SqlErrorInfo {
        final int code;
        final String message;

        SqlErrorInfo(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    // [kguanoluisa] - Clasifica por código numérico SQL primero (exacto), luego por texto como respaldo - 21/05/2026
    private void classifySqlError(SqlErrorInfo sqlInfo, Map<String, Object> response) {
        String sqlError = sqlInfo.message;

        if (sqlInfo.code == SQL_ERR_TABLA_INEXISTENTE ||
                (sqlError.contains("The specified table") && sqlError.contains("is not in the database"))) {
            String tableName = extractNameAfterMarker(sqlError, "The specified table");
            String msg = tableName != null
                    ? "La tabla especificada (" + tableName + ") no existe en la base de datos."
                    : "La tabla especificada no existe en la base de datos.";
            setError(response, msg, "ERROR_TABLA_INEXISTENTE");

        } else if (sqlInfo.code == SQL_ERR_COLUMNA_INEXISTENTE ||
                (sqlError.contains("not found in any table") || sqlError.contains("Column ("))) {
            String marker = sqlError.contains("Column (") ? "Column (" : null;
            String colName = extractNameAfterMarker(sqlError, marker);
            String msg = colName != null
                    ? "La columna especificada (" + colName + ") no existe en la tabla de la base de datos."
                    : "La columna especificada no existe en la tabla de la base de datos.";
            setError(response, msg, "ERROR_COLUMNA_INEXISTENTE");

        } else if (sqlInfo.code == SQL_ERR_REGISTRO_DUPLICADO ||
                (sqlError.contains("Unique constraint") && sqlError.contains("violated"))) {
            String procName = extractCallName(sqlError);
            String msg = procName != null
                    ? "Registro duplicado en el procedimiento (" + procName + "): ya existe un registro con esos datos en la base de datos."
                    : "Registro duplicado: ya existe un registro con esos datos en la base de datos.";
            setError(response, msg, "ERROR_REGISTRO_DUPLICADO");

        } else if (sqlInfo.code == SQL_ERR_ROUTINE_INEXISTENTE ||
                (sqlError.contains("Routine (") && sqlError.contains("can not be resolved"))) {
            String routineName = extractNameAfterMarker(sqlError, "Routine");
            String msg = routineName != null
                    ? "El procedimiento o rutina especificada (" + routineName + ") no existe en la base de datos."
                    : "El procedimiento o rutina especificada no existe en la base de datos.";
            setError(response, msg, "ERROR_ROUTINE_INEXISTENTE");

        } else {
            setError(response, "Error de base de datos (código " + sqlInfo.code + "): " + sqlError, "ERROR_BASE_DATOS");
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

    // [kguanoluisa] - Recorre la cadena de causas buscando el error SQL raíz.
    // Prioridad: instanceof SQLException (tiene código numérico exacto) → texto del mensaje de Hibernate.
    // - 21/05/2026
    private SqlErrorInfo getRootSqlError(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx) {
                return new SqlErrorInfo(sqlEx.getErrorCode(), sqlEx.getMessage());
            }
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("is not in the database") ||
                    msg.contains("The specified table") ||
                    msg.contains("Unique constraint") ||
                    msg.contains("SQL Error:") ||
                    msg.contains("table (") ||
                    msg.contains("not found in any table") ||
                    msg.contains("Column (") ||
                    msg.contains("Routine (") ||
                    msg.contains("can not be resolved") ||
                    msg.contains("andctrlvirlogin"))) {
                // No es SQLException directa, extraer código del mensaje si aparece (ej: "SQL Error: -217")
                int code = extractSqlCode(msg);
                return new SqlErrorInfo(code, msg);
            }
            cause = cause.getCause();
        }
        return null;
    }

    // [kguanoluisa] - Intenta extraer el código numérico SQL del mensaje de texto de Hibernate.
    // Formato: "SQL Error: -217, SQLState: IX000" → retorna -217. Si no encuentra, retorna 0.
    // - 21/05/2026
    private int extractSqlCode(String msg) {
        int idx = msg.indexOf("SQL Error: ");
        if (idx == -1) return 0;
        try {
            int start = idx + 10;
            int end = msg.indexOf(",", start);
            String codeStr = (end != -1 ? msg.substring(start, end) : msg.substring(start)).trim();
            return Integer.parseInt(codeStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
