package com.fastfood.comprobante.service;

import com.fastfood.comprobante.exception.ComprobanteException;
import com.fastfood.comprobante.model.ComprobanteDetalle;
import com.fastfood.comprobante.repository.ComprobanteRepository;
import com.fastfood.comprobante.soap.contract.*;
import jakarta.validation.Validator;
import com.fastfood.comprobante.validation.VentaValidacion;
import javax.xml.datatype.DatatypeFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ComprobanteService {
    private final ComprobanteRepository comprobantes;
    private final Validator validator;
    private final JsonMapper mapper;
    private final TransactionTemplate transaccion;

    public ComprobanteService(ComprobanteRepository comprobantes, Validator validator,
            JsonMapper mapper, PlatformTransactionManager manager) {
        this.comprobantes = comprobantes;
        this.validator = validator;
        this.mapper = mapper;
        transaccion = new TransactionTemplate(manager);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public GenerarComprobanteResponse generar(Venta venta) {
        normalizarYValidar(venta);
        String huella = huella(venta);
        GenerarComprobanteResponse anterior = buscarExistente(venta.getPedidoId(), huella);
        if (anterior != null) {
            return anterior;
        }
        try {
            return transaccion.execute(status -> {
                var entidad = new com.fastfood.comprobante.model.Comprobante();
                entidad.setPedidoId(venta.getPedidoId());
                entidad.setNumero(String.format(Locale.ROOT, "FFA-%06d", venta.getPedidoId()));
                entidad.setFechaEmision(Instant.now().truncatedTo(ChronoUnit.MICROS));
                entidad.setClienteId(venta.getClienteId());
                entidad.setClienteNombres(venta.getClienteNombres());
                entidad.setClienteCorreo(venta.getClienteCorreo());
                entidad.setMetodoPago(com.fastfood.comprobante.model.Comprobante.MetodoPago.valueOf(venta.getMetodoPago()));
                entidad.setSubtotal(venta.getSubtotal());
                entidad.setImpuesto(venta.getImpuesto());
                entidad.setTotal(venta.getTotal());
                entidad.setHuellaContenido(huella);
                for (Detalle linea : venta.getDetalle()) {
                    ComprobanteDetalle detalle = new ComprobanteDetalle();
                    detalle.setComprobante(entidad);
                    detalle.setProductoId(linea.getProductoId());
                    detalle.setNombreProducto(linea.getNombreProducto());
                    detalle.setCantidad(linea.getCantidad());
                    detalle.setPrecioUnitario(linea.getPrecioUnitario());
                    detalle.setSubtotal(linea.getSubtotal());
                    detalle.setObservacion(linea.getObservacion());
                    entidad.getDetalles().add(detalle);
                }
                comprobantes.saveAndFlush(entidad);
                return respuesta(entidad, true);
            });
        } catch (DataIntegrityViolationException ex) {
            // El insert fallido termino; recuperar la emision ganadora en otra transaccion.
            GenerarComprobanteResponse ganador = buscarExistente(venta.getPedidoId(), huella);
            if (ganador != null) {
                return ganador;
            }
            throw new ComprobanteException("ERROR_INTERNO", "No se pudo emitir el comprobante.");
        }
    }

    public ConsultarComprobanteResponse consultar(Long pedidoId) {
        if (pedidoId == null || pedidoId <= 0) {
            throw validacion();
        }
        return transaccion.execute(status -> {
            var entidad = comprobantes.findByPedidoId(pedidoId).orElseThrow(() ->
                    new ComprobanteException("COMPROBANTE_NO_ENCONTRADO", "El comprobante aun no fue emitido."));
            ConsultarComprobanteResponse respuesta = new ConsultarComprobanteResponse();
            respuesta.setComprobante(convertir(entidad));
            return respuesta;
        });
    }

    private GenerarComprobanteResponse buscarExistente(Long pedidoId, String huella) {
        return transaccion.execute(status -> comprobantes.findByPedidoId(pedidoId).map(entidad -> {
            if (!entidad.getHuellaContenido().equals(huella)) {
                throw new ComprobanteException("DATOS_COMPROBANTE_CONFLICTO",
                        "El pedido ya tiene un comprobante con otros datos.");
            }
            return respuesta(entidad, false);
        }).orElse(null));
    }

    private GenerarComprobanteResponse respuesta(com.fastfood.comprobante.model.Comprobante entidad, boolean creado) {
        GenerarComprobanteResponse response = new GenerarComprobanteResponse();
        response.setComprobante(convertir(entidad));
        response.setCreado(creado);
        return response;
    }

    private Comprobante convertir(com.fastfood.comprobante.model.Comprobante entidad) {
        Venta venta = new Venta();
        venta.setPedidoId(entidad.getPedidoId());
        venta.setClienteId(entidad.getClienteId());
        venta.setClienteNombres(entidad.getClienteNombres());
        venta.setClienteCorreo(entidad.getClienteCorreo());
        venta.setMetodoPago(entidad.getMetodoPago().name());
        venta.setSubtotal(entidad.getSubtotal());
        venta.setImpuesto(entidad.getImpuesto());
        venta.setTotal(entidad.getTotal());
        for (ComprobanteDetalle linea : entidad.getDetalles()) {
            Detalle detalle = new Detalle();
            detalle.setProductoId(linea.getProductoId());
            detalle.setNombreProducto(linea.getNombreProducto());
            detalle.setCantidad(linea.getCantidad());
            detalle.setPrecioUnitario(linea.getPrecioUnitario());
            detalle.setSubtotal(linea.getSubtotal());
            detalle.setObservacion(linea.getObservacion());
            venta.getDetalle().add(detalle);
        }
        Comprobante response = new Comprobante();
        response.setId(entidad.getId());
        response.setNumero(entidad.getNumero());
        response.setFechaEmision(DatatypeFactory.newDefaultInstance()
                .newXMLGregorianCalendar(entidad.getFechaEmision().toString()));
        response.setVenta(venta);
        return response;
    }

    private void normalizarYValidar(Venta venta) {
        if (venta == null || !validator.validate(VentaValidacion.de(venta)).isEmpty()) {
            throw validacion();
        }
        venta.setClienteNombres(venta.getClienteNombres().trim());
        venta.setClienteCorreo(venta.getClienteCorreo().trim().toLowerCase(Locale.ROOT));
        BigDecimal suma = BigDecimal.ZERO;
        for (Detalle detalle : venta.getDetalle()) {
            if (detalle == null || detalle.getPrecioUnitario().multiply(BigDecimal.valueOf(detalle.getCantidad()))
                            .compareTo(detalle.getSubtotal()) != 0) {
                throw validacion();
            }
            detalle.setNombreProducto(detalle.getNombreProducto().trim());
            String observacion = detalle.getObservacion();
            detalle.setObservacion(observacion == null || observacion.isBlank() ? null : observacion.trim());
            detalle.setPrecioUnitario(detalle.getPrecioUnitario().setScale(2));
            detalle.setSubtotal(detalle.getSubtotal().setScale(2));
            suma = suma.add(detalle.getSubtotal());
        }
        if (venta.getImpuesto().signum() != 0 || suma.compareTo(venta.getTotal()) != 0
                || venta.getSubtotal().compareTo(venta.getTotal()) != 0) {
            throw validacion();
        }
        venta.setSubtotal(venta.getSubtotal().setScale(2));
        venta.setImpuesto(BigDecimal.ZERO.setScale(2));
        venta.setTotal(venta.getTotal().setScale(2));
    }

    private String huella(Venta venta) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(VentaValidacion.de(venta)).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible");
        }
    }

    private ComprobanteException validacion() {
        return new ComprobanteException("VALIDACION", "Los datos de la venta no son validos.");
    }
}
