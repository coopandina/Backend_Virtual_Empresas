package apiVirtualEmpresa.apiVirtualEmpresa.login.controller;


import apiVirtualEmpresa.apiVirtualEmpresa.login.dto.CodSegurdiad;
import apiVirtualEmpresa.apiVirtualEmpresa.login.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/verificar")
@RequiredArgsConstructor
@Slf4j
public class VerifiController {
    @Autowired
    private AuthService authService;

    /**
     * Endpoint para verificar Token seguridad Login
     */
    @PostMapping(value = "/codigo_seguridad")
    public ResponseEntity<Map<String, Object>> valCodiSeguridad(Authentication authentication, HttpServletRequest request, @RequestBody CodSegurdiad codSeguridad) {
        log.info("--- Iniciando valCodiSeguridad en VerifiController ---");
        log.info("Datos recibidos: {}", codSeguridad);
        if (authentication != null) {
            log.info("Autenticacion - isAuthenticated: {}, Name: {}", authentication.isAuthenticated(), authentication.getName());
        } else {
            log.info("Autenticacion es null");
        }

        ResponseEntity<Map<String, Object>> response = authService.validarCodSeguridad(request, codSeguridad, authentication);
        
        log.info("Respuesta valCodiSeguridad - Status: {}", response.getStatusCode());
        log.info("Respuesta valCodiSeguridad - Body: {}", response.getBody());
        log.info("--- Fin valCodiSeguridad ---");
        
        return response;
    }

    /**
     * Endpoint para aceptar términos y condiciones e iniciar sesión definitivamente
     */
    @PostMapping(value = "/terminos-condiciones")
    public ResponseEntity<Map<String, Object>> aceptarTerminos(Authentication authentication, HttpServletRequest request, @RequestBody(required = false) Map<String, String> body) {
        return authService.aceptarTerminosCondiciones(request, authentication, body);
    }

}
