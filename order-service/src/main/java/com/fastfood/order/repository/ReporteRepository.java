package com.fastfood.order.repository;

import com.fastfood.order.dto.ReporteResponse.*;
import com.fastfood.order.model.Pago.*;
import com.fastfood.order.model.Pedido.EstadoPedido;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import java.math.*;
import java.sql.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ReporteRepository {
    private final JdbcTemplate jdbc;

    private static final String PEDIDOS = " FROM pedidos p JOIN pagos g ON g.pedido_id=p.id ";
    private static final String VENTAS = PEDIDOS + " WHERE g.estado_pago='PAGADO' AND p.estado_pedido<>'CANCELADO' AND g.fecha_pago>=? AND g.fecha_pago<? ";
    private static final String CREADOS = PEDIDOS + " WHERE p.fecha_creacion>=? AND p.fecha_creacion<? ";
    private static final String PRODUCTOS = " FROM pedido_detalles d JOIN pedidos p ON p.id=d.pedido_id JOIN pagos g ON g.pedido_id=p.id "
            + " WHERE g.estado_pago='PAGADO' AND p.estado_pedido<>'CANCELADO' AND g.fecha_pago>=? AND g.fecha_pago<? ";

    public List<PedidoFila> pedidos(boolean ventas, String inicio, String fin, String filtro, int limit, long offset) {
        String where = (ventas ? VENTAS : CREADOS) + filtroPedidos(ventas, filtro);
        List<Object> args = argumentos(inicio, fin, filtro);
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT p.id,p.cliente_nombres,p.estado_pedido,p.total,p.fecha_creacion,g.fecha_pago,g.metodo_pago,g.estado_pago"
                + where + " ORDER BY " + (ventas ? "g.fecha_pago" : "p.fecha_creacion") + " DESC,p.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> pedido(rs, ventas), args.toArray());
    }

    public VentasResumen ventasResumen(String inicio, String fin, String metodo) {
        var resumen = new VentasResumen();
        for (MetodoPago valor : MetodoPago.values()) resumen.getVentasPorMetodo().put(valor, new BigDecimal("0.00"));
        jdbc.query("SELECT g.metodo_pago,COUNT(*) cantidad,SUM(p.total) importe" + VENTAS + filtroPedidos(true, metodo)
                + " GROUP BY g.metodo_pago", rs -> {
                    resumen.setPedidosConVenta(resumen.getPedidosConVenta() + rs.getLong("cantidad"));
                    BigDecimal importe = rs.getBigDecimal("importe");
                    resumen.setTotalVentas(resumen.getTotalVentas().add(importe));
                    resumen.getVentasPorMetodo().put(MetodoPago.valueOf(rs.getString("metodo_pago")), importe);
                }, argumentos(inicio, fin, metodo).toArray());
        if (resumen.getPedidosConVenta() > 0) resumen.setTicketPromedio(resumen.getTotalVentas()
                .divide(BigDecimal.valueOf(resumen.getPedidosConVenta()), 2, RoundingMode.HALF_UP));
        return resumen;
    }

    public PedidosResumen pedidosResumen(String inicio, String fin, String estado) {
        var resumen = new PedidosResumen();
        for (EstadoPedido valor : EstadoPedido.values()) resumen.getPedidosPorEstado().put(valor, 0L);
        jdbc.query("SELECT p.estado_pedido,COUNT(*) cantidad" + CREADOS + filtroPedidos(false, estado)
                + " GROUP BY p.estado_pedido", rs -> {
                    long cantidad = rs.getLong("cantidad");
                    resumen.setTotalPedidos(resumen.getTotalPedidos() + cantidad);
                    resumen.getPedidosPorEstado().put(EstadoPedido.valueOf(rs.getString("estado_pedido")), cantidad);
                }, argumentos(inicio, fin, estado).toArray());
        return resumen;
    }

    public long cantidadProductos(String inicio, String fin, Long categoria) {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT d.producto_id)" + PRODUCTOS + filtroCategoria(categoria),
                Long.class, argumentos(inicio, fin, categoria).toArray());
    }

    public ProductosResumen productosResumen(String inicio, String fin, Long categoria) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(d.cantidad),0) unidades,COALESCE(SUM(d.subtotal),0) importe"
                + PRODUCTOS + filtroCategoria(categoria), (rs, row) -> {
                    var resumen = new ProductosResumen();
                    resumen.setUnidadesVendidas(rs.getLong("unidades"));
                    resumen.setTotalVentasProductos(rs.getBigDecimal("importe").setScale(2));
                    return resumen;
                }, argumentos(inicio, fin, categoria).toArray());
    }

    public List<ProductoFila> productos(String inicio, String fin, Long categoria, int limit, long offset) {
        List<Object> args = argumentos(inicio, fin, categoria);
        args.add(limit);
        args.add(offset);
        // Elegir el nombre historico del detalle mas reciente de cada producto.
        String consulta = "SELECT producto_id,MAX(CASE WHEN posicion=1 THEN nombre_producto END) nombre_producto,"
                + "SUM(cantidad) unidades,SUM(subtotal) importe FROM (SELECT d.producto_id,d.nombre_producto,d.cantidad,d.subtotal,"
                + "ROW_NUMBER() OVER(PARTITION BY d.producto_id ORDER BY g.fecha_pago DESC,p.id DESC,d.id DESC) posicion"
                + PRODUCTOS + filtroCategoria(categoria) + ") lineas GROUP BY producto_id"
                + " ORDER BY unidades DESC,importe DESC,producto_id ASC LIMIT ? OFFSET ?";
        return jdbc.query(consulta, (rs, row) -> {
            var dto = new ProductoFila();
            dto.setProductoId(rs.getLong("producto_id"));
            dto.setNombreProducto(rs.getString("nombre_producto"));
            dto.setUnidades(rs.getLong("unidades"));
            dto.setImporteVentas(rs.getBigDecimal("importe"));
            return dto;
        }, args.toArray());
    }

    private PedidoFila pedido(ResultSet rs, boolean ventas) throws SQLException {
        var dto = new PedidoFila();
        dto.setPedidoId(rs.getLong("id"));
        dto.setNumeroPedido(String.format(Locale.ROOT, "FF-%06d", dto.getPedidoId()));
        dto.setClienteNombres(rs.getString("cliente_nombres"));
        dto.setTotal(rs.getBigDecimal("total"));
        dto.setMetodoPago(MetodoPago.valueOf(rs.getString("metodo_pago")));
        dto.setEstadoPago(EstadoPago.valueOf(rs.getString("estado_pago")));
        dto.setEstadoPedido(EstadoPedido.valueOf(rs.getString("estado_pedido")));
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        if (ventas) dto.setFechaPago(rs.getTimestamp("fecha_pago", utc).toInstant());
        else dto.setFechaCreacion(rs.getTimestamp("fecha_creacion", utc).toInstant());
        return dto;
    }

    private String filtroPedidos(boolean ventas, String filtro) {
        return filtro == null ? "" : (ventas ? " AND g.metodo_pago=? " : " AND p.estado_pedido=? ");
    }
    private String filtroCategoria(Long categoria) { return categoria == null ? "" : " AND d.categoria_id=? "; }
    private List<Object> argumentos(String inicio, String fin, Object filtro) {
        var args = new ArrayList<Object>(List.of(inicio, fin)); if (filtro != null) args.add(filtro);
        return args;
    }
}
