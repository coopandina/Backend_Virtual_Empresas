package apiVirtualEmpresa.apiVirtualEmpresa.dto.captec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountDTO {
    private String identificationNumber;
    private String identificationType;
    private String accountType;
    private String accountNumber;
    private String accountCode;
    private String token;
    private String accountHolder;
    private String cellphone;
    private String fiCode;
    private String aba;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String email;
}
