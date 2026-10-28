package com.fastfood.order.dto;

import com.fastfood.order.model.Pedido;
import com.fastfood.order.model.Pedido.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class PedidoResumenResponse {

    private Long id;
    private String numero;
    private PedidoDetalleResponse.ClienteSnapshot cliente;
    private Instant fechaCreacion;
    private EstadoPedido estadoPedido;
    private TipoEntrega tipoEntrega;
    private BigDecimal total;
    private Long version;
    private PagoResponse pago;
    private Long conversacionId;

    public static PedidoResumenResponse de(Pedido pedido) {
        PedidoResumenResponse dto = new PedidoResumenResponse();
        dto.setId(pedido.getId());
        dto.setNumero(String.format(java.util.Locale.ROOT, "FF-%06d", pedido.getId()));
        dto.setCliente(
                new PedidoDetalleResponse.ClienteSnapshot(
                        pedido.getClienteId(),
                        pedido.getClienteNombres(),
                        pedido.getClienteCorreo(),
                        pedido.getClienteTelefono()));
        dto.setFechaCreacion(pedido.getFechaCreacion());
        dto.setEstadoPedido(pedido.getEstadoPedido());
        dto.setTipoEntrega(pedido.getTipoEntrega());
        dto.setTotal(pedido.getTotal());
        dto.setVersion(pedido.getVersion());
        dto.setPago(PagoResponse.de(pedido.getPago()));
        dto.setConversacionId(pedido.getConversacion().getId());
        return dto;
    }
}
