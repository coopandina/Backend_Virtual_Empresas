package apiVirtualEmpresa.apiVirtualEmpresa.Controller;

import apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.*;
import apiVirtualEmpresa.apiVirtualEmpresa.services.MetodoPagoClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ordenante")
public class MetodoPagoController {

    private final MetodoPagoClientService metodoPagoClientService;

    public MetodoPagoController(MetodoPagoClientService metodoPagoClientService) {
        this.metodoPagoClientService = metodoPagoClientService;
    }

    @PostMapping("/validated-entity")
    public ResponseEntity<ValidatedEntityResponse> verifyRecipient(@RequestBody ValidatedEntityRequest request) {
        ValidatedEntityResponse response = metodoPagoClientService.verifyRecipient(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bank-transfer")
    public ResponseEntity<BankTransferResponse> executeTransfer(@RequestBody BankTransferRequest request) {
        BankTransferResponse response = metodoPagoClientService.executeTransfer(request);
        return ResponseEntity.ok(response);
    }
}
