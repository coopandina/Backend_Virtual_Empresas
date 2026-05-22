package apiVirtualEmpresa.apiVirtualEmpresa.VerificarCuenta.Controller;

import apiVirtualEmpresa.apiVirtualEmpresa.VerificarCuenta.Service.VerificarCuentaService;
import apiVirtualEmpresa.apiVirtualEmpresa.VerificarCuenta.dto.VerificarCuentaUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/verificarCuenta")
public class VerificarCuentaController {

    private final VerificarCuentaService verificarCuentaService;

    public VerificarCuentaController(VerificarCuentaService verificarCuentaService) {
        this.verificarCuentaService = verificarCuentaService;
    }

    @PostMapping("/verificar-cuenta")
    public ResponseEntity<Map<String, Object>> verificarCuenta(HttpServletRequest request, Authentication authentication, @RequestBody VerificarCuentaUtils dto) {
        return verificarCuentaService.VerificarRestriccionesCuenta(request, authentication, dto);
    }
}
