package apiVirtualEmpresa.apiVirtualEmpresa.dto.captec;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ValidatedEntityResponse extends CaptecResponse<ValidatedEntityResult> {
}
