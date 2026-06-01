package apiVirtualEmpresa.apiVirtualEmpresa.VerificarCuenta.Service;

import apiVirtualEmpresa.apiVirtualEmpresa.VerificarCuenta.dto.VerificarCuentaUtils;
import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.config.Obtenertoken;
import apiVirtualEmpresa.apiVirtualEmpresa.login.service.TokenExpirationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class VerificarCuentaService {

    @Autowired
    private TokenExpirationService tokenExpirationService;

    @PersistenceContext
    private EntityManager entityManager;

    private final JwtUtil jwtUtil;

    public VerificarCuentaService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public ResponseEntity<Map<String, Object>> VerificarRestriccionesCuenta(HttpServletRequest request, Authentication authentication, VerificarCuentaUtils dto) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();
        try {

            String token = Obtenertoken.desdeCookie(request);

            if (token == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA027");
                err.put("errors", "No autorizado: no fue posible obtener el token.");
                allDataList.add(err);

                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            if (authentication == null || !authentication.isAuthenticated()) {

                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                allDataList.add(err);

                response.put("success", false);
                response.put("message", "No autorizado");
                response.put("AllData", allDataList);

                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            String rucUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            String codCta = dto.getCodCta();

            String sql = """
                    SELECT bloctad_cod_estbloc, bloctad_msj_bloc
                    FROM andbloctad 
                    WHERE bloctad_cod_cuent = :cuenta
                      AND bloctad_cod_estbloc <> :estado
                    """;

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("cuenta", codCta);
            query.setParameter("estado", 2);
            List<Object[]> results = query.getResultList();

            Map<String, Object> data = new HashMap<>();
            data.put("cuenta", codCta);

            // restrinccion
            if (!results.isEmpty()) {
                Object[] row = results.get(0);
                String descripcion = row[1] != null ? row[1].toString() : "Cuenta con restricción";
                data.put("descripcion", descripcion != null ? descripcion.trim() : null);
                response.put("success", false);
                response.put("allData", List.of(data));
                return new ResponseEntity<>(response, HttpStatus.OK);
            }

            data.put("descripcion", "Cuenta sin restricciones");
            response.put("success", true);
            response.put("allData", List.of(data));

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            //kguanoluisa, [Se reemplazo la respuesta JSON de error por RuntimeException manteniendo el mensaje original][N/A][22/05/2026]
            throw new RuntimeException("Error interno del servidor: " + e.getMessage(), e);
        }
    }
}
