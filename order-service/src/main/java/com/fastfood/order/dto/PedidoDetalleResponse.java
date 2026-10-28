package com.fastfood.order.dto;

import com.fastfood.order.model.Pedido;
import com.fastfood.order.model.Pedido.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Data
public class PedidoDetalleResponse {

    private Long id;
    private String numero;
    private ClienteSnapshot cliente;
    private UUID cotizacionId;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private EstadoPedido estadoPedido;
    private TipoEntrega tipoEntrega;
    private BigDecimal total;
    private Long version;
    private List<DetallePedidoResponse> detalles;
    private PagoResponse pago;
    private Long conversacionId;
    private String motivoCancelacion;

    public static PedidoDetalleResponse de(Pedido pedido) {
        PedidoDetalleResponse dto = new PedidoDetalleResponse();
        dto.setId(pedido.getId());
        dto.setNumero(String.format(java.util.Locale.ROOT, "FF-%06d", pedido.getId()));
        dto.setCliente(
                new ClienteSnapshot(
                        pedido.getClienteId(),
                        pedido.getClienteNombres(),
                        pedido.getClienteCorreo(),
                        pedido.getClienteTelefono()));
        dto.setCotizacionId(pedido.getCotizacionId());
        dto.setFechaCreacion(pedido.getFechaCreacion());
        dto.setFechaActualizacion(pedido.getFechaActualizacion());
        dto.setEstadoPedido(pedido.getEstadoPedido());
        dto.setTipoEntrega(pedido.getTipoEntrega());
        dto.setTotal(pedido.getTotal());
        dto.setVersion(pedido.getVersion());
        dto.setDetalles(pedido.getDetalles().stream().map(DetallePedidoResponse::de).toList());
        dto.setPago(PagoResponse.de(pedido.getPago()));
        dto.setConversacionId(pedido.getConversacion().getId());
        dto.setMotivoCancelacion(pedido.getMotivoCancelacion());
        return dto;
    }

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ClienteSnapshot {
        private Long id;
        private String nombres;
        private String correo;
        private String telefono;
    }
}
