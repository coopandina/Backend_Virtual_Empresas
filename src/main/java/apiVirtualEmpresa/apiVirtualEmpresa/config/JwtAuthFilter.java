package apiVirtualEmpresa.apiVirtualEmpresa.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        //  ENDPOINT SIN TOKEN
        if (path.equals("/api/password/firmar")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = getJwtFromCookie(request);

        if (token != null && jwtUtil.validateToken(token)) {
            // [kguanoluisa] - Control de Concurrencia: Verificar si la sesión del token sigue activa en Base de Datos - 12/05/2026
            String sessionId = jwtUtil.getSessionIdFromToken(token);
            String ruc = jwtUtil.getrucIdenClie(token);
            String username = jwtUtil.getUsernameFromToken(token);

            if (sessionId != null
                    && !path.startsWith("/api/auth/")
                    && !path.startsWith("/api/firma-sri/")
                    && !path.equals("/api/verificar/codigo_seguridad")
                    && !path.equals("/api/verificar/terminos-condiciones")
                    && !path.equals("/api/password/firmar")) {

                try {
                    String sqlCheck = "SELECT COUNT(*) FROM andctrlvirlogin " +
                            "WHERE ctrlvirlogin_ide_virtual = :ruc " +
                            "AND ctrlvirlogin_user_virtual = :user " +
                            "AND ctrlvirlogin_cod_temporal = :uuid " +
                            "AND ctrlvirlogin_ctrl_virtual = 1";
                    jakarta.persistence.Query qCheck = entityManager.createNativeQuery(sqlCheck);
                    qCheck.setParameter("ruc", ruc);
                    qCheck.setParameter("user", username);
                    qCheck.setParameter("uuid", sessionId);

                    Number count = (Number) qCheck.getSingleResult();

                    if (count.intValue() == 0) {
                        // Sesión inactiva, bloqueada o expirada por concurrencia
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"errors\": \"Sesión inactiva o abierta en otro dispositivo.\", \"status\": \"AASESIONACTIVA\"}");
                        return;
                    }

                    // Registrar Latido de Actividad (Heartbeat)
                    org.springframework.transaction.support.TransactionTemplate tt =
                            new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                    tt.execute(status -> {
                        String sqlHeartbeat = "UPDATE andctrlvirlogin SET ctrlvirlogin_fecha_virtual = CURRENT " +
                                "WHERE ctrlvirlogin_cod_temporal = :uuid";
                        jakarta.persistence.Query qHeart = entityManager.createNativeQuery(sqlHeartbeat);
                        qHeart.setParameter("uuid", sessionId);
                        qHeart.executeUpdate();
                        return null;
                    });

                } catch (Exception ex) {
                    // Si falla la consulta a la tabla de control, dejamos continuar la petición por estabilidad
                }
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }


    private String getJwtFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
