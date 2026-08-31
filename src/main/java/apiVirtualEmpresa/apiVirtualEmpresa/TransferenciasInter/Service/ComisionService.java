package apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasInter.Service;

import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.config.Obtenertoken;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ComisionService {

    @PersistenceContext
    private EntityManager entityManager;

    private final JwtUtil jwtUtil;

    public ComisionService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public ResponseEntity<Map<String, Object>> calcularComision(HttpServletRequest request, Authentication authentication, String cuenta, String tipo) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Validar token y autenticación
            String token = Obtenertoken.desdeCookie(request);
            if (token == null) {
                response.put("status", "AA027");
                response.put("errors", "No autorizado: no fue posible obtener el token.");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("status", "AA028");
                response.put("errors", "La sesión no es válida o ha expirado.");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            // 2. Validar número de cuenta
            if (cuenta == null || cuenta.trim().isEmpty()) {
                response.put("status", "ERROR_PARAM");
                response.put("errors", "El parámetro 'cuenta' es requerido.");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // 3. Obtener datos del cliente a partir de la cuenta
            String sqlCliente = "SELECT FIRST 1 " +
                    "clien_ide_clien, " +
                    "clien_cod_empre, " +
                    "clien_cod_ofici " +
                    "FROM cnxctadp, cnxclien " +
                    "WHERE ctadp_cod_ctadp = :cuenta " +
                    "AND ctadp_cod_depos IN (1, 9) " +
                    "AND ctadp_cod_ectad = '1' " +
                    "AND ctadp_cod_clien = clien_cod_clien";

            Query queryCliente = entityManager.createNativeQuery(sqlCliente);
            queryCliente.setParameter("cuenta", cuenta.trim());
            List<Object[]> rsCliente = queryCliente.getResultList();

            if (rsCliente.isEmpty()) {
                response.put("status", "ERROR_CTA_NO_ENCONTRADA");
                response.put("errors", "No se encontró la cuenta especificada.");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            Object[] clienteData = rsCliente.get(0);
            String clientIdentification = clienteData[0] != null ? clienteData[0].toString().trim() : "";
            Integer clientCodEmpre = clienteData[1] != null ? Integer.valueOf(clienteData[1].toString().trim()) : 69;
            Integer clientCodOfici = clienteData[2] != null ? Integer.valueOf(clienteData[2].toString().trim()) : 1;

            BigDecimal valComision = null;
            String ctrlComision = "0";

            // 4. Buscar comisión personalizada en andcmcempr (SOLO para transferencias directas)
            if (tipo != null && tipo.trim().equalsIgnoreCase("directa")) {
                String sqlComisione = "SELECT cmcempr_comic_cmcempr, cmcempr_ctrl_cmcempr FROM andcmcempr " +
                        "WHERE cmcempr_ide_clien = :idclien ";
                Query queryComisione = entityManager.createNativeQuery(sqlComisione);
                queryComisione.setParameter("idclien", clientIdentification);

                List<?> rsComisione = queryComisione.getResultList();
                if (!rsComisione.isEmpty() && rsComisione.get(0) != null) {
                    Object[] fila = (Object[]) rsComisione.get(0);
                    if (fila[0] != null) {
                        valComision = new BigDecimal(fila[0].toString().trim());
                    }
                    if (fila[1] != null) {
                        ctrlComision = fila[1].toString().trim();
                    }
                }
            }

            // 5. Si no tiene comisión especial, buscar en cnxcomic
            if (ctrlComision.equals("0")) {
                String sqlComision = "SELECT comic_val_comic FROM cnxcomic " +
                        "WHERE comic_cod_comic = 5 " +
                        "AND comic_cod_ofici = :codOfici " +
                        "AND comic_cod_empre = :codEmpre";
                Query queryComision = entityManager.createNativeQuery(sqlComision);
                queryComision.setParameter("codOfici", clientCodOfici);
                queryComision.setParameter("codEmpre", clientCodEmpre);
                List<?> rsComision = queryComision.getResultList();
                if (!rsComision.isEmpty() && rsComision.get(0) != null) {
                    valComision = new BigDecimal(rsComision.get(0).toString().trim());
                }
            }

            if (valComision == null) {
                response.put("status", "ERROR_CONFIG_COMISION");
                response.put("errors", "No se encontró configuración de comisión para esta cuenta.");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // 6. Llamar al procedimiento andprc_cal_iva para calcular el IVA
            String sqlIva = "CALL andprc_cal_iva(:codEmpre, :cuenta, :comision)";
            Query queryIva = entityManager.createNativeQuery(sqlIva);
            queryIva.setParameter("codEmpre", clientCodEmpre);
            queryIva.setParameter("cuenta", cuenta.trim());
            queryIva.setParameter("comision", valComision.toString());

            List<?> rsIva = queryIva.getResultList();

            if (!rsIva.isEmpty() && rsIva.get(0) != null) {
                Object[] filaIva = (Object[]) rsIva.get(0);
                BigDecimal valorIva = filaIva[0] != null ? new BigDecimal(filaIva[0].toString().trim()) : BigDecimal.ZERO;
                BigDecimal valorComision = filaIva[1] != null ? new BigDecimal(filaIva[1].toString().trim()) : valComision;
                BigDecimal totalIvaComision = filaIva[2] != null ? new BigDecimal(filaIva[2].toString().trim()) : valComision;

                response.put("success", true);
                response.put("comision", valorComision.doubleValue());
                response.put("iva", valorIva.doubleValue());
                response.put("total", totalIvaComision.doubleValue());
            } else {
                // Fallback: si el procedimiento no devuelve datos, calcular manualmente
                response.put("success", true);
                response.put("comision", valComision.doubleValue());
                response.put("iva", 0.0);
                response.put("total", valComision.doubleValue());
            }

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "ERROR_INTERNO");
            response.put("errors", "Error interno al calcular la comisión: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
