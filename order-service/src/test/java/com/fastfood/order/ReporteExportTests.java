package com.fastfood.order;

import com.fastfood.order.dto.ReporteResponse;
import com.fastfood.order.dto.ReporteResponse.ProductoFila;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.service.*;
import com.fastfood.order.service.ReporteExportService.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReporteExportTests {
    @TempDir Path directorio;
    private final LocalDate fecha = LocalDate.of(2001, 1, 1);

    @Test
    void exportacionIncluyeTodasLasPaginasYEliminaElTemporalAlCerrar() throws Exception {
        ReporteService reportes = mock(ReporteService.class);
        when(reportes.productos(fecha, fecha, null, 0, 100)).thenReturn(pagina(0, 100, 103));
        when(reportes.productos(fecha, fecha, null, 1, 100)).thenReturn(pagina(1, 3, 103));
        var exportar = new ReporteExportService(reportes, directorio.toString());
        Path temporal;
        try (Archivo archivo = exportar.generar(Tipo.productos, Formato.CSV, fecha, fecha, null, null, null)) {
            temporal = archivo.getRuta();
            assertEquals(104, Files.readAllLines(temporal).size());
            assertTrue(Files.readString(temporal).contains("Producto 102"));
            verify(reportes).productos(fecha, fecha, null, 1, 100);
        }
        assertFalse(Files.exists(temporal));
    }

    @Test
    void excesoDeFilasSeRechazaAntesDeCrearElArchivo() throws Exception {
        ReporteService reportes = mock(ReporteService.class);
        when(reportes.productos(fecha, fecha, null, 0, 100)).thenReturn(pagina(0, 100, 10001));
        var exportar = new ReporteExportService(reportes, directorio.toString());
        var error = assertThrows(NegocioException.class, () -> exportar.generar(Tipo.productos,
                Formato.CSV, fecha, fecha, null, null, null));
        assertEquals("EXPORTACION_DEMASIADO_GRANDE", error.getCode());
        try (var archivos = Files.list(directorio)) { assertEquals(0, archivos.count()); }
    }

    @Test
    void fallaDeEscrituraLimpiaElArchivoSinEntregarUnReporteTruncado() throws Exception {
        ReporteService reportes = mock(ReporteService.class);
        var pagina = pagina(0, 1, 1);
        pagina.getContent().get(0).setImporteVentas(null);
        when(reportes.productos(fecha, fecha, null, 0, 100)).thenReturn(pagina);
        var exportar = new ReporteExportService(reportes, directorio.toString());
        assertThrows(RuntimeException.class, () -> exportar.generar(Tipo.productos, Formato.TXT, fecha, fecha, null, null, null));
        try (var archivos = Files.list(directorio)) { assertEquals(0, archivos.count()); }
    }

    private ReporteResponse<ProductoFila> pagina(int page, int cantidad, long total) {
        var pagina = new ReporteResponse<ProductoFila>();
        pagina.setPage(page); pagina.setSize(100); pagina.setTotalElements(total);
        pagina.setTotalPages((int) ((total + 99) / 100));
        List<ProductoFila> filas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            var fila = new ProductoFila(); fila.setProductoId((long) (page * 100 + i));
            fila.setNombreProducto("Producto " + (page * 100 + i)); fila.setUnidades(2);
            fila.setImporteVentas(new BigDecimal("25.00")); filas.add(fila);
        }
        pagina.setContent(filas); return pagina;
    }
}
