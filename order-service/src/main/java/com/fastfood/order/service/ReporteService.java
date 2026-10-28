package com.fastfood.order.service;

import com.fastfood.order.dto.*;
import com.fastfood.order.dto.ReporteResponse.*;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.model.Pago.MetodoPago;
import com.fastfood.order.model.Pedido.EstadoPedido;
import com.fastfood.order.repository.ReporteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {
    public static final ZoneId ZONA = ZoneId.of("America/Lima");
    private final ReporteRepository repository;

    public ReporteResponse<PedidoFila> ventas(LocalDate desde, LocalDate hasta, MetodoPago metodo, int page, int size) {
        validar(desde, hasta, page, size);
        String inicio = inicio(desde), fin = inicio(hasta.plusDays(1));
        String filtro = metodo == null ? null : metodo.name();
        var resumen = repository.ventasResumen(inicio, fin, filtro);
        return respuesta(repository.pedidos(true, inicio, fin, filtro, size, (long) page * size),
                resumen.getPedidosConVenta(), desde, hasta, page, size, resumen);
    }

    public ReporteResponse<PedidoFila> pedidos(LocalDate desde, LocalDate hasta, EstadoPedido estado, int page, int size) {
        validar(desde, hasta, page, size);
        String inicio = inicio(desde), fin = inicio(hasta.plusDays(1));
        String filtro = estado == null ? null : estado.name();
        var resumen = repository.pedidosResumen(inicio, fin, filtro);
        return respuesta(repository.pedidos(false, inicio, fin, filtro, size, (long) page * size),
                resumen.getTotalPedidos(), desde, hasta, page, size, resumen);
    }

    public ReporteResponse<ProductoFila> productos(LocalDate desde, LocalDate hasta, Long categoria, int page, int size) {
        validar(desde, hasta, page, size);
        if (categoria != null && categoria <= 0) throw validacion();
        String inicio = inicio(desde), fin = inicio(hasta.plusDays(1));
        return respuesta(repository.productos(inicio, fin, categoria, size, (long) page * size),
                repository.cantidadProductos(inicio, fin, categoria), desde, hasta, page, size,
                repository.productosResumen(inicio, fin, categoria));
    }

    public DashboardResponse dashboard() {
        LocalDate hoy = LocalDate.now(ZONA);
        String inicio = inicio(hoy), fin = inicio(hoy.plusDays(1));
        var resumen = repository.ventasResumen(inicio, fin, null);
        var estados = repository.pedidosResumen(inicio, fin, null);
        var productos = repository.productos(inicio, fin, null, 1, 0);
        var dto = new DashboardResponse();
        dto.setFechaLocal(hoy);
        dto.setTotalVentas(resumen.getTotalVentas());
        dto.setPedidosConVenta(resumen.getPedidosConVenta());
        dto.setTicketPromedio(resumen.getTicketPromedio());
        dto.setPedidosRegistrados(estados.getTotalPedidos());
        dto.setPedidosPorEstado(estados.getPedidosPorEstado());
        dto.setProductoMasVendido(productos.isEmpty() ? null : productos.get(0));
        return dto;
    }

    public void validar(LocalDate desde, LocalDate hasta, int page, int size) {
        if (desde == null || hasta == null || hasta.isBefore(desde)
                || ChronoUnit.DAYS.between(desde, hasta) >= 366 || hasta.equals(LocalDate.MAX)
                || page < 0 || size < 1 || size > 100) throw validacion();
    }

    private String inicio(LocalDate fecha) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
                .format(fecha.atStartOfDay(ZONA).toInstant());
    }

    private <T> ReporteResponse<T> respuesta(List<T> content, long total, LocalDate desde, LocalDate hasta,
            int page, int size, Object resumen) {
        var dto = new ReporteResponse<T>();
        dto.setContent(content);
        dto.setTotalElements(total);
        dto.setTotalPages((int) ((total + size - 1) / size));
        dto.setPage(page);
        dto.setSize(size);
        dto.setDesde(desde);
        dto.setHasta(hasta);
        dto.setResumen(resumen);
        return dto;
    }

    private NegocioException validacion() {
        return new NegocioException(400, "VALIDACION", "Revisa el periodo (maximo 366 dias), los filtros y la paginacion.");
    }
}
