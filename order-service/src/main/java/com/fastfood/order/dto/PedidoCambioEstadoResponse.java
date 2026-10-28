package com.fastfood.order.dto;

import com.fastfood.order.model.Pedido.EstadoPedido;
import com.fastfood.order.model.PedidoCambioEstado;

import lombok.Data;

import java.time.Instant;

@Data
public class PedidoCambioEstadoResponse {

    private Long id;
    private EstadoPedido anterior;
    private EstadoPedido nuevo;
    private Long actorId;
    private String motivo;
    private Instant fecha;

    public static PedidoCambioEstadoResponse de(PedidoCambioEstado cambio) {
        PedidoCambioEstadoResponse dto = new PedidoCambioEstadoResponse();
        dto.setId(cambio.getId());
        dto.setAnterior(cambio.getAnterior());
        dto.setNuevo(cambio.getNuevo());
        dto.setActorId(cambio.getActorId());
        dto.setMotivo(cambio.getMotivo());
        dto.setFecha(cambio.getFecha());
        return dto;
    }
}
