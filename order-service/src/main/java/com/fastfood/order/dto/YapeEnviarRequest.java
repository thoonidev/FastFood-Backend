package com.fastfood.order.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class YapeEnviarRequest {

    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{6,40}", message = "Debe tener de 6 a 40 caracteres alfanumericos o guiones")
    private String referenciaOperacion;

    @NotNull
    @PositiveOrZero
    private Long versionPagoEsperada;
}
