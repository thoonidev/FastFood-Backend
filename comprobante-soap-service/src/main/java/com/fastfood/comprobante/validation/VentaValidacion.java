package com.fastfood.comprobante.validation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.*;

/** Mantiene las validaciones y el JSON usado por las huellas anteriores a XJC. */
public final class VentaValidacion {
    private VentaValidacion() { }
    @Data
    public static class Detalle {
        @NotNull @Positive private Long productoId;
        @NotBlank @Size(max = 100) private String nombreProducto;
        @NotNull @Min(1) @Max(99) private Integer cantidad;
        @NotNull @Positive @Digits(integer = 10, fraction = 2) private BigDecimal precioUnitario;
        @NotNull @Positive @Digits(integer = 10, fraction = 2) private BigDecimal subtotal;
        @Size(max = 250) private String observacion;
    }
    @Data
    public static class Venta {
        @NotNull @Positive private Long pedidoId;
        @NotNull @Positive private Long clienteId;
        @NotBlank @Size(max = 201) private String clienteNombres;
        @NotBlank @Email @Size(max = 150) private String clienteCorreo;
        @NotBlank @Pattern(regexp = "EFECTIVO|YAPE|TARJETA") private String metodoPago;
        @NotNull @Positive @Digits(integer = 10, fraction = 2) private BigDecimal subtotal;
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) private BigDecimal impuesto;
        @NotNull @Positive @Digits(integer = 10, fraction = 2) private BigDecimal total;
        @Valid @NotEmpty @Size(max = 40)
        private List<Detalle> detalle = new ArrayList<>();
    }
    public static Venta de(com.fastfood.comprobante.soap.contract.Venta fuente) {
        Venta venta = new Venta();
        venta.setPedidoId(fuente.getPedidoId());
        venta.setClienteId(fuente.getClienteId());
        venta.setClienteNombres(fuente.getClienteNombres());
        venta.setClienteCorreo(fuente.getClienteCorreo());
        venta.setMetodoPago(fuente.getMetodoPago());
        venta.setSubtotal(fuente.getSubtotal());
        venta.setImpuesto(fuente.getImpuesto());
        venta.setTotal(fuente.getTotal());
        for (var linea : fuente.getDetalle()) {
            if (linea == null) { venta.getDetalle().add(null); continue; }
            Detalle detalle = new Detalle();
            detalle.setProductoId(linea.getProductoId());
            detalle.setNombreProducto(linea.getNombreProducto());
            detalle.setCantidad(linea.getCantidad());
            detalle.setPrecioUnitario(linea.getPrecioUnitario());
            detalle.setSubtotal(linea.getSubtotal());
            detalle.setObservacion(linea.getObservacion());
            venta.getDetalle().add(detalle);
        }
        return venta;
    }
}
