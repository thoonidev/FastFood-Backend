package com.fastfood.order.dto;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class PedidoCancelarRequest {

    @NotBlank
    @Size(max = 250)
    private String motivo;

    @NotNull @PositiveOrZero private Long versionEsperada;
}
