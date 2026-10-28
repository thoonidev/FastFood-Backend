package com.fastfood.order.dto;

import com.fastfood.order.model.Conversacion;
import com.fastfood.order.model.Pago.EstadoPago;
import com.fastfood.order.model.Pedido.EstadoPedido;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
public class ConversacionResponse {

    private Long pedidoId;
    private String numeroPedido;
    private Cliente cliente;
    private EstadoPedido estadoPedido;
    private EstadoPago estadoPago;
    private BigDecimal total;
    private Long conversacionId;
    private Conversacion.EstadoConversacion estadoConversacion;
    private Long ultimaSecuencia;
    private boolean puedeEnviar;
    private boolean pendienteRespuesta;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cliente {
        private Long id;
        private String nombres;
    }

    public static ConversacionResponse de(Conversacion conversacion) {
        var pedido = conversacion.getPedido();
        ConversacionResponse dto = new ConversacionResponse();
        dto.setPedidoId(pedido.getId());
        dto.setNumeroPedido(String.format("FF-%06d", pedido.getId()));
        dto.setCliente(new Cliente(pedido.getClienteId(), pedido.getClienteNombres()));
        dto.setEstadoPedido(pedido.getEstadoPedido());
        dto.setEstadoPago(pedido.getPago().getEstadoPago());
        dto.setTotal(pedido.getTotal());
        dto.setConversacionId(conversacion.getId());
        dto.setEstadoConversacion(conversacion.getEstado());
        dto.setUltimaSecuencia(conversacion.getUltimaSecuencia());
        dto.setPuedeEnviar(conversacion.getEstado() == Conversacion.EstadoConversacion.ABIERTA
                && pedido.getEstadoPedido() != EstadoPedido.CANCELADO
                && pedido.getEstadoPedido() != EstadoPedido.ENTREGADO);
        dto.setPendienteRespuesta(conversacion.getUltimaSecuenciaCliente() > conversacion.getUltimaSecuenciaPersonal());
        return dto;
    }
}
