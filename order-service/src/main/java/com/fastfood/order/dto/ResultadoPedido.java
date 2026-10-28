package com.fastfood.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResultadoPedido {
    private PedidoDetalleResponse pedido;
    private int status;
    private boolean replayed;
}
