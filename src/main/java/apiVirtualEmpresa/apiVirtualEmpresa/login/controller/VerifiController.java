package apiVirtualEmpresa.apiVirtualEmpresa.login.controller;



import apiVirtualEmpresa.apiVirtualEmpresa.login.dto.CodSegurdiad;
import apiVirtualEmpresa.apiVirtualEmpresa.login.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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

public class VerifiController {
    @Autowired
    private AuthService authService;
    /**
     * Endpoint para verificar Token seguridad Login
     */
    @PostMapping(value = "/codigo_seguridad")
    public ResponseEntity<Map<String, Object>> valCodiSeguridad(Authentication authentication, HttpServletRequest request, @RequestBody CodSegurdiad codSeguridad) {
        //System.out.println("hola beeeeeeeeebeeeeeeeeeeeeeeeeee "+authentication.isAuthenticated());
        return authService.validarCodSeguridad(request, codSeguridad,authentication);
    }

}
