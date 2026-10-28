package com.fastfood.order.service;

import com.fastfood.order.dto.ReporteResponse;
import com.fastfood.order.dto.ReporteResponse.*;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.model.Pago.MetodoPago;
import com.fastfood.order.model.Pedido.EstadoPedido;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReporteExportService {
    public enum Formato { CSV, TXT }
    public enum Tipo { ventas, pedidos, productos }
    private final ReporteService reportes;
    private final Path directorio;

    public ReporteExportService(ReporteService reportes, @Value("${fastfood.report.directorio}") String directorio) {
        this.reportes = reportes;
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public Archivo generar(Tipo tipo, Formato formato, LocalDate desde, LocalDate hasta,
            MetodoPago metodo, EstadoPedido estado, Long categoria) throws IOException {
        validarFiltros(tipo, metodo, estado, categoria);
        ReporteResponse<?> primera = consultar(tipo, desde, hasta, metodo, estado, categoria, 0);
        if (primera.getTotalElements() > 10000) throw new NegocioException(400, "EXPORTACION_DEMASIADO_GRANDE",
                "El reporte supera 10000 filas. Selecciona un periodo menor.");
        Files.createDirectories(directorio);
        Path temporal = Files.createTempFile(directorio, "fastfood-reporte-", ".tmp");
        boolean terminado = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporal, StandardCharsets.UTF_8)) {
                if (formato == Formato.CSV) writer.write('\uFEFF');
                else {
                    writer.write("Reporte: " + tipo + "\nPeriodo: " + desde + " a " + hasta + "\nMoneda: PEN\n");
                    writer.write("Filtros: metodoPago=" + metodo + ", estadoPedido=" + estado + ", categoriaId=" + categoria + "\n");
                }
                escribir(writer, formato, tipo == Tipo.productos
                        ? List.of("desde", "hasta", "productoId", "producto", "unidades", "importeVentas")
                        : List.of("desde", "hasta", tipo == Tipo.ventas ? "fechaPago" : "fechaCreacion",
                                "numeroPedido", "cliente", "metodoPago", "estadoPedido", "estadoPago", "total"));
                int page = 0;
                ReporteResponse<?> lote = primera;
                do {
                    for (Object fila : lote.getContent()) escribir(writer, formato, columnas(tipo, desde, hasta, fila));
                    page++;
                    if (page < primera.getTotalPages()) lote = consultar(tipo, desde, hasta, metodo, estado, categoria, page);
                } while (page < primera.getTotalPages());
            }
            String nombre = tipo + "_" + desde + "_" + hasta + "." + formato.name().toLowerCase(Locale.ROOT);
            Archivo archivo = new Archivo(temporal, nombre, formato);
            terminado = true;
            return archivo;
        } finally {
            if (!terminado) Files.deleteIfExists(temporal);
        }
    }

    public static void validarFiltros(Tipo tipo, MetodoPago metodo, EstadoPedido estado, Long categoria) {
        if ((tipo != Tipo.ventas && metodo != null) || (tipo != Tipo.pedidos && estado != null)
                || (tipo != Tipo.productos && categoria != null)) throw new NegocioException(400, "VALIDACION",
                        "El filtro no corresponde al tipo de reporte.");
    }

    private ReporteResponse<?> consultar(Tipo tipo, LocalDate desde, LocalDate hasta, MetodoPago metodo,
            EstadoPedido estado, Long categoria, int page) {
        return switch (tipo) {
            case ventas -> reportes.ventas(desde, hasta, metodo, page, 100);
            case pedidos -> reportes.pedidos(desde, hasta, estado, page, 100);
            case productos -> reportes.productos(desde, hasta, categoria, page, 100);
        };
    }

    private List<String> columnas(Tipo tipo, LocalDate desde, LocalDate hasta, Object fila) {
        if (fila instanceof ProductoFila producto) return List.of(desde.toString(), hasta.toString(),
                producto.getProductoId().toString(), producto.getNombreProducto(), Long.toString(producto.getUnidades()),
                importe(producto.getImporteVentas()));
        PedidoFila pedido = (PedidoFila) fila;
        var fecha = tipo == Tipo.ventas ? pedido.getFechaPago() : pedido.getFechaCreacion();
        String local = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(fecha.atZone(ReporteService.ZONA));
        return List.of(desde.toString(), hasta.toString(), local, pedido.getNumeroPedido(), pedido.getClienteNombres(),
                pedido.getMetodoPago().name(), pedido.getEstadoPedido().name(), pedido.getEstadoPago().name(), importe(pedido.getTotal()));
    }

    private String importe(BigDecimal valor) { return valor.setScale(2).toPlainString(); }

    private void escribir(BufferedWriter writer, Formato formato, List<String> columnas) throws IOException {
        List<String> valores = new ArrayList<>();
        for (int i = 0; i < columnas.size(); i++) {
            String valor = columnas.get(i);
            if (formato == Formato.CSV) {
                // Los importes y cantidades se escriben como numeros; proteger los textos.
                boolean texto = i != columnas.size() - 1;
                if (texto && !valor.isEmpty() && ("=+-@".indexOf(valor.charAt(0)) >= 0 || Character.isISOControl(valor.charAt(0))))
                    valor = "'" + valor;
                valor = "\"" + valor.replace("\"", "\"\"") + "\"";
            } else valor = valor.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
            valores.add(valor);
        }
        writer.write(String.join(formato == Formato.CSV ? "," : "\t", valores));
        writer.newLine();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class Archivo implements AutoCloseable {
        private final Path ruta;
        private final String nombre;
        private final Formato formato;
        @Override public void close() throws IOException { Files.deleteIfExists(ruta); }
    }
}
