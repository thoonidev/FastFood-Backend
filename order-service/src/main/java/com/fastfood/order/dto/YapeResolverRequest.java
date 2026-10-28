package com.fastfood.order.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class YapeResolverRequest {

    @NotNull
    @Positive
    private Long intentoId;

    @NotNull
    private Boolean aprobar;

    @Size(max = 250)
    private String motivo;

    @NotNull
    @PositiveOrZero
    private Long versionPagoEsperada;
}
