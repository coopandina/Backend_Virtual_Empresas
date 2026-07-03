package apiVirtualEmpresa.apiVirtualEmpresa.dto.captec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BankTransferResult {
    private String authorizationNumber;
    private String accountingDate;
    private String cellphone;
}
