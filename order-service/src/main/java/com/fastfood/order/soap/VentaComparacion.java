package com.fastfood.order.soap;

import com.fastfood.order.soap.contract.Venta;
import com.fastfood.order.soap.contract.Detalle;
import java.math.BigDecimal;
import java.util.List;

/** JAXB no genera equals: se verifica el contenido historico, no la identidad del objeto. */
public final class VentaComparacion {
    private VentaComparacion() { }

    public static boolean coincide(Venta esperada, Venta recibida) {
        return esperada != null && recibida != null && datos(esperada).equals(datos(recibida));
    }

    private static Datos datos(Venta venta) {
        return new Datos(venta.getPedidoId(), venta.getClienteId(), venta.getClienteNombres(),
                venta.getClienteCorreo(), venta.getMetodoPago(), venta.getSubtotal(), venta.getImpuesto(),
                venta.getTotal(), venta.getDetalle().stream().map(VentaComparacion::linea).toList());
    }

    private static Linea linea(Detalle detalle) {
        if (detalle == null) return null;
        return new Linea(detalle.getProductoId(), detalle.getNombreProducto(), detalle.getCantidad(),
                detalle.getPrecioUnitario(), detalle.getSubtotal(), detalle.getObservacion());
    }

    private record Datos(long pedidoId, long clienteId, String nombres, String correo, String metodo,
            BigDecimal subtotal, BigDecimal impuesto, BigDecimal total, List<Linea> detalles) { }
    private record Linea(long productoId, String nombre, int cantidad, BigDecimal precio,
            BigDecimal subtotal, String observacion) { }
}
