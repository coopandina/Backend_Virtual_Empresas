package apiVirtualEmpresa.apiVirtualEmpresa.dto.captec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BankTransferRequest {
    private String entityId;
    private String originNetwork;
    private String terminalSeq;
    private String terminalId;
    private String txId;
    private Object systemid;
    private String txtcaja;
    private AccountDTO sourceAccount;
    private AccountDTO destinationAccount;
    private BigDecimal amount;
    private BigDecimal comission;
    private String currency;
    private String description;
    private String city;
    private String endToEndId;
    
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String verificationCode;
}
