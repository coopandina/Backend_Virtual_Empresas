package apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasInter.Controller;

import apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasInter.dto.TransfInterUtils;
import apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasInter.Service.TransfInterService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/transfInter")
@RequiredArgsConstructor
public class TransfInterController {

    private final TransfInterService transfInterService;

    @GetMapping("/listCtaDebitInter")
    public ResponseEntity<Map<String, Object>>listCuentBeneficiarios(HttpServletRequest request,Authentication authentication) {
        return transfInterService.lisCtaTransferibles(request, authentication);
    }

    //lista de instituciones financieras para transferencias interbancarias
    @GetMapping("/listarInsFinancieras")
    public ResponseEntity<Map<String,Object>>listarInstituciones(HttpServletRequest request,Authentication authentication){
        return transfInterService.listarInstFinancieras(request, authentication);
    }

    //Codigo de Transferencias interbancarias
    @PostMapping("/codTempInterbancarias")
    public ResponseEntity<Map<String, Object>>codTempInterbancarias(HttpServletRequest request, Authentication authentication, @RequestBody TransfInterUtils dto){
        return transfInterService.genCodInterbancarias(request,authentication, dto);
    }

    @PostMapping("/srtGrabarInterbn")
    public ResponseEntity<Map<String, Object>>srtGrabarInterbn(HttpServletRequest request, Authentication authentication, @RequestBody TransfInterUtils dto) {
        return transfInterService.srtGrabarInterban(request,authentication, dto);
    }
}
