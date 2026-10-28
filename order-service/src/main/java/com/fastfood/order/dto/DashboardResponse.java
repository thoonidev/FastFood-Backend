package com.fastfood.order.dto;

import com.fastfood.order.dto.ReporteResponse.ProductoFila;
import com.fastfood.order.model.Pedido.EstadoPedido;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
public class DashboardResponse {
    private LocalDate fechaLocal;
    private String moneda = "PEN";
    private BigDecimal totalVentas;
    private long pedidosRegistrados;
    private long pedidosConVenta;
    private BigDecimal ticketPromedio;
    private ProductoFila productoMasVendido;
    private Map<EstadoPedido, Long> pedidosPorEstado;
}
