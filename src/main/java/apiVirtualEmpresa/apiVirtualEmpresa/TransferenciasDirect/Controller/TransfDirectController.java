package apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.Controller;
import apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.Service.TransfDirectService;
import apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.dto.TransfDirectUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transfDirect")


public class TransfDirectController {

    private final TransfDirectService transfDirectService;

    @PostMapping("/codTempDirectas")
    public ResponseEntity<Map<String, Object>>codTempDirectas(HttpServletRequest request, Authentication authentication, @RequestBody TransfDirectUtils dto) {
        return transfDirectService.genCodDirectas(request, authentication, dto);
    }

    @PostMapping("/srtGrabarDirectas")
    public ResponseEntity<Map<String, Object>>srtGrabarDir(HttpServletRequest request, Authentication authentication, @RequestBody TransfDirectUtils dto) {
        return transfDirectService.srtGrabarDir(request,authentication, dto);
    }


}