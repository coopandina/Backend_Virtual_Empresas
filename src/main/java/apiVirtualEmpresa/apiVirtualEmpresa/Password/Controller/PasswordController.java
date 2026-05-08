package apiVirtualEmpresa.apiVirtualEmpresa.Password.Controller;
import apiVirtualEmpresa.apiVirtualEmpresa.Password.Service.PasswordService;
import apiVirtualEmpresa.apiVirtualEmpresa.Password.dto.PasswordUtils;
import apiVirtualEmpresa.apiVirtualEmpresa.dashboard.dto.DashboardUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import srijava.XAdESBESSignature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/password")

public class PasswordController {

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    // CAMBIO DE CONTRASEÑA
    @PostMapping("/cambioPassword")
    public ResponseEntity<Map<String,Object>> cambioPassword(HttpServletRequest request, @RequestBody PasswordUtils passwordUtils, Authentication authentication){
        return passwordService.cambioPassword(request, passwordUtils, authentication);
    }
    // CAMBIO DE CONTRASEÑA
    @PostMapping("/codcambioPassword")
    public ResponseEntity<Map<String,Object>>codcambioPassword(HttpServletRequest request, @RequestBody PasswordUtils passwordUtils, Authentication authentication){
        return passwordService.codcambioPassword(request, passwordUtils, authentication);
    }



}
