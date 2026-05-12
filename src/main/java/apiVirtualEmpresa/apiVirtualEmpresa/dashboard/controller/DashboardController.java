package apiVirtualEmpresa.apiVirtualEmpresa.dashboard.controller;

import apiVirtualEmpresa.apiVirtualEmpresa.dashboard.dto.DashboardUtils;
import apiVirtualEmpresa.apiVirtualEmpresa.dashboard.service.DashboardService;
import envioCorreo.sendEmail;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sms.SendSMS;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    //informacion de la datos del socio legeado
    @GetMapping("/infodatos")
    public ResponseEntity<Map<String, Object>> informacionSocio(HttpServletRequest request,Authentication authentication) {
        return dashboardService.informacionSocio(request, authentication);
    }

    //informacion de la cuentas
    @GetMapping("/infocta")
    public ResponseEntity<Map<String, Object>> informacionCta(HttpServletRequest request,Authentication authentication) {
        return dashboardService.inforCtaDepos(request, authentication);
    }

    //transferencias interbancarias
        @PostMapping("/ctaPropiasTransfer")
        public ResponseEntity<Map<String, Object>> ctaPropiasTransfer(HttpServletRequest request,@RequestBody DashboardUtils dashboardUtils,Authentication authentication) {
            return dashboardService.ctaPropiasTrans(request, dashboardUtils, authentication);
        }

    //ver informacion de terceros
    @PostMapping("/VerInfTerceros")
    public ResponseEntity<Map<String,Object>>VerInfTerceros(HttpServletRequest request, @RequestBody DashboardUtils dashboardUtils){
        return dashboardService.VerInfTerceros(request, dashboardUtils);
    }

    @GetMapping("/ultimoMovi")
    public ResponseEntity<Map<String,Object>>ultimosMovimientos(HttpServletRequest request, Authentication authentication){
        return dashboardService.ultimosMovimientos(request, authentication);
    }
    //listar movimientos con rango de fechas
    @PostMapping("/ultimoMoviFecha")
    public ResponseEntity<Map<String,Object>>ultimosMovimientosFecha(HttpServletRequest request, @RequestBody DashboardUtils dashboardUtils, Authentication authentication){
        return dashboardService.ultimosMovimientosFecha(request, dashboardUtils, authentication);
    }

    // [kguanoluisa] - Endpoint para registrar aceptación de ley de protección de datos - 12/05/2026
    @PostMapping("/aceptarPolitica")
    public ResponseEntity<Map<String, Object>> aceptarPoliticaDatos(HttpServletRequest request, Authentication authentication) {
        return dashboardService.aceptarPoliticaDatos(request, authentication);
    }

}
