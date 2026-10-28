package com.fastfood.order.dto;

import com.fastfood.order.soap.contract.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class ComprobanteResponse {
    private Long id;
    private String numero;
    private Instant fechaEmision;
    private Long pedidoId;
    private Cliente cliente;
    private String metodoPago;
    private BigDecimal subtotal;
    private BigDecimal impuesto;
    private BigDecimal total;
    private List<Detalle> detalles;
    private boolean validezTributaria = false;

    @Data
    @lombok.AllArgsConstructor
    public static class Cliente {
        private Long id;
        private String nombres;
        private String correo;
    }

    public static ComprobanteResponse de(Comprobante comprobante) {
        Venta venta = comprobante.getVenta();
        ComprobanteResponse dto = new ComprobanteResponse();
        dto.setId(comprobante.getId());
        dto.setNumero(comprobante.getNumero());
        dto.setFechaEmision(Instant.parse(comprobante.getFechaEmision().toXMLFormat()));
        dto.setPedidoId(venta.getPedidoId());
        dto.setCliente(new Cliente(venta.getClienteId(), venta.getClienteNombres(), venta.getClienteCorreo()));
        dto.setMetodoPago(venta.getMetodoPago());
        dto.setSubtotal(venta.getSubtotal());
        dto.setImpuesto(venta.getImpuesto());
        dto.setTotal(venta.getTotal());
        dto.setDetalles(venta.getDetalle());
        return dto;
    }
}
