package com.fastfood.order.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class EfectivoConfirmarRequest {

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    private BigDecimal montoRecibido;

    @NotNull
    @PositiveOrZero
    private Long versionPagoEsperada;
}
