package com.fastfood.order;

import com.fastfood.order.soap.VentaComparacion;
import com.fastfood.order.soap.contract.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class VentaComparacionTests {
    @Test
    void aceptaObjetosDistintosConLaMismaVentaYRechazaAlteraciones() {
        Venta original = venta(), recibida = venta();
        assertNotSame(original, recibida);
        assertTrue(VentaComparacion.coincide(original, recibida));
        recibida.getDetalle().get(0).setObservacion("Otra observacion");
        assertFalse(VentaComparacion.coincide(original, recibida));
        recibida = venta(); recibida.setClienteId(2);
        assertFalse(VentaComparacion.coincide(original, recibida));
        recibida = venta(); recibida.setTotal(new BigDecimal("26.00"));
        assertFalse(VentaComparacion.coincide(original, recibida));
        recibida = venta(); recibida.getDetalle().clear();
        assertFalse(VentaComparacion.coincide(original, recibida));
        assertFalse(VentaComparacion.coincide(original, null));
    }

    private Venta venta() {
        Venta venta = new Venta();
        venta.setPedidoId(1); venta.setClienteId(1);
        venta.setClienteNombres("Ana Perez"); venta.setClienteCorreo("ana@fastfood.test");
        venta.setMetodoPago("TARJETA"); venta.setSubtotal(new BigDecimal("25.00"));
        venta.setImpuesto(new BigDecimal("0.00")); venta.setTotal(new BigDecimal("25.00"));
        Detalle detalle = new Detalle(); detalle.setProductoId(1); detalle.setNombreProducto("Hamburguesa");
        detalle.setCantidad(2); detalle.setPrecioUnitario(new BigDecimal("12.50"));
        detalle.setSubtotal(new BigDecimal("25.00")); detalle.setObservacion("Sin cebolla");
        venta.getDetalle().add(detalle);
        return venta;
    }
}
