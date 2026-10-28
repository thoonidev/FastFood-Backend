package com.fastfood.order.dto;

import com.fastfood.order.model.Pago.MetodoPago;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PedidoCrearRequest {

    @NotNull private UUID cotizacionId;

    @NotNull private MetodoPago metodoPago;

    @DecimalMin("0.01")
    @DecimalMax("9999999999.99")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal montoAnunciado;
}
