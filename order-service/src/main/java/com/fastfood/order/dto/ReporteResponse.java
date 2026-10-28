package com.fastfood.order.dto;

import com.fastfood.order.model.Pago.*;
import com.fastfood.order.model.Pedido.EstadoPedido;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Data
public class ReporteResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private LocalDate desde;
    private LocalDate hasta;
    private String moneda = "PEN";
    private Object resumen;

    @Data
    public static class PedidoFila {
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private Instant fechaCreacion;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private Instant fechaPago;
        private Long pedidoId;
        private String numeroPedido;
        private String clienteNombres;
        private MetodoPago metodoPago;
        private EstadoPedido estadoPedido;
        private EstadoPago estadoPago;
        private BigDecimal total;
    }

    @Data
    public static class ProductoFila {
        private Long productoId;
        private String nombreProducto;
        private long unidades;
        private BigDecimal importeVentas;
    }

    @Data
    public static class VentasResumen {
        private BigDecimal totalVentas = new BigDecimal("0.00");
        private long pedidosConVenta;
        private BigDecimal ticketPromedio = new BigDecimal("0.00");
        private Map<MetodoPago, BigDecimal> ventasPorMetodo = new EnumMap<>(MetodoPago.class);
    }

    @Data
    public static class PedidosResumen {
        private long totalPedidos;
        private Map<EstadoPedido, Long> pedidosPorEstado = new EnumMap<>(EstadoPedido.class);
    }

    @Data
    public static class ProductosResumen {
        private long unidadesVendidas;
        private BigDecimal totalVentasProductos;
    }
}
