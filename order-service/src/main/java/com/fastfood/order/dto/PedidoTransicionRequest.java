package com.fastfood.order.dto;

import com.fastfood.order.model.Pedido.EstadoPedido;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class PedidoTransicionRequest {

    @NotNull private EstadoPedido estadoDestino;

    @NotNull @PositiveOrZero private Long versionEsperada;
}
