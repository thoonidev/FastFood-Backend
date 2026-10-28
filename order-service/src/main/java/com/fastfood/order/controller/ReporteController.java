package com.fastfood.order.controller;

import com.fastfood.order.dto.*;
import com.fastfood.order.dto.ReporteResponse.*;
import com.fastfood.order.model.Pago.MetodoPago;
import com.fastfood.order.model.Pedido.EstadoPedido;
import com.fastfood.order.service.*;
import com.fastfood.order.service.ReporteExportService.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ReporteController {
    private final ReporteService reportes;
    private final ReporteExportService archivos;

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() { return reportes.dashboard(); }

    @GetMapping("/reportes/ventas")
    public ReporteResponse<PedidoFila> ventas(@RequestParam LocalDate desde, @RequestParam LocalDate hasta,
            @RequestParam(required = false) MetodoPago metodoPago,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reportes.ventas(desde, hasta, metodoPago, page, size);
    }

    @GetMapping("/reportes/pedidos")
    public ReporteResponse<PedidoFila> pedidos(@RequestParam LocalDate desde, @RequestParam LocalDate hasta,
            @RequestParam(required = false) EstadoPedido estadoPedido,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reportes.pedidos(desde, hasta, estadoPedido, page, size);
    }

    @GetMapping("/reportes/productos")
    public ReporteResponse<ProductoFila> productos(@RequestParam LocalDate desde, @RequestParam LocalDate hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return reportes.productos(desde, hasta, categoriaId, page, size);
    }

    @GetMapping("/reportes/{tipo}/exportacion")
    public void exportar(@PathVariable Tipo tipo, @RequestParam Formato formato,
            @RequestParam LocalDate desde, @RequestParam LocalDate hasta,
            @RequestParam(required = false) MetodoPago metodoPago, @RequestParam(required = false) EstadoPedido estadoPedido,
            @RequestParam(required = false) Long categoriaId, HttpServletResponse response) throws IOException {
        try (Archivo archivo = archivos.generar(tipo, formato, desde, hasta, metodoPago, estadoPedido, categoriaId)) {
            response.setContentType(archivo.getFormato() == Formato.CSV ? "text/csv;charset=UTF-8" : "text/plain;charset=UTF-8");
            response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(archivo.getNombre()).build().toString());
            response.setHeader("Cache-Control", "no-store");
            response.setContentLengthLong(Files.size(archivo.getRuta()));
            try (InputStream input = Files.newInputStream(archivo.getRuta())) {
                input.transferTo(response.getOutputStream());
                response.flushBuffer();
            }
        }
    }
}
