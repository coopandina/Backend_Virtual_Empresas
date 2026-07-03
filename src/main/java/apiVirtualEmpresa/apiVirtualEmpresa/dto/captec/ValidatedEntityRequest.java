package apiVirtualEmpresa.apiVirtualEmpresa.dto.captec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ValidatedEntityRequest {
    private String entityId;
    private String originNetwork;
    private String terminalSeq;
    private String terminalId;
    private String txId;
    private Object systemid;
    private AccountDTO sourceAccount;
    private AccountDTO destinationAccount;
    private String description;
    private String city;
    private String endToEndId;
}
