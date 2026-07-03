package apiVirtualEmpresa.apiVirtualEmpresa.dto.captec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ValidatedEntityResult {
    private String accountingDate;
    private String cellphone;
    private String accountHolder;
    private String errorCode;
    private String errorMessageCli;
    private String errorMessageTec;
}
