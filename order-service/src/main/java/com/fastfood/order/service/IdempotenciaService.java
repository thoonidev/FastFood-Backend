package com.fastfood.order.service;

import com.fastfood.order.dto.*;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.model.SolicitudIdempotente;
import com.fastfood.order.repository.SolicitudIdempotenteRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
public class IdempotenciaService {

    private final SolicitudIdempotenteRepository solicitudes;
    private final JsonMapper mapper;
    private final TransactionTemplate transaccion;

    public IdempotenciaService(
            SolicitudIdempotenteRepository solicitudes,
            JsonMapper mapper,
            PlatformTransactionManager manager) {
        this.solicitudes = solicitudes;
        this.mapper = mapper;
        transaccion = new TransactionTemplate(manager);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public ResultadoPedido buscar(Long actor, String operacion, UUID clave, String huella) {
        return solicitudes
                .findByActorIdAndOperacionAndClave(actor, operacion, clave)
                .map(registro -> recuperar(registro, huella))
                .orElse(null);
    }

    public ResultadoPedido ejecutar(
            Long actor,
            String operacion,
            UUID clave,
            String huella,
            Supplier<ResultadoPedido> accion) {
        ResultadoPedido anterior = buscar(actor, operacion, clave, huella);
        if (anterior != null) {
            return anterior;
        }
        try {
            return transaccion.execute(
                    status -> {
                        SolicitudIdempotente registro = new SolicitudIdempotente();
                        registro.setActorId(actor);
                        registro.setOperacion(operacion);
                        registro.setClave(clave);
                        registro.setHuella(huella);
                        solicitudes.saveAndFlush(registro);

                        ResultadoPedido resultado = accion.get();
                        registro.setRecursoTipo("PEDIDO");
                        registro.setRecursoId(resultado.getPedido().getId().toString());
                        registro.setHttpStatus(resultado.getStatus());
                        registro.setResponseJson(mapper.writeValueAsString(resultado.getPedido()));
                        registro.setCompletadaEn(Instant.now());
                        solicitudes.saveAndFlush(registro);
                        return resultado;
                    });
        } catch (DataIntegrityViolationException ex) {
            // La transaccion fallida ya termino; leer el resultado ganador en otra transaccion.
            ResultadoPedido ganador = buscar(actor, operacion, clave, huella);
            if (ganador != null) {
                return ganador;
            }
            throw new NegocioException(
                    409,
                    "OPERACION_CONCURRENTE",
                    "La operacion cambio. Reintenta con la misma clave.");
        }
    }

    private ResultadoPedido recuperar(SolicitudIdempotente registro, String huella) {
        if (!registro.getHuella().equals(huella)) {
            throw new NegocioException(
                    409, "IDEMPOTENCIA_CONFLICTO", "La clave ya fue usada con otros datos.");
        }
        PedidoDetalleResponse pedido =
                mapper.readValue(registro.getResponseJson(), PedidoDetalleResponse.class);
        return new ResultadoPedido(pedido, registro.getHttpStatus(), true);
    }

    public ResultadoPago buscarPago(Long actor, String operacion, UUID clave, String huella) {
        return solicitudes.findByActorIdAndOperacionAndClave(actor, operacion, clave)
                .map(registro -> {
                    if (!registro.getHuella().equals(huella)) {
                        throw new NegocioException(409, "IDEMPOTENCIA_CONFLICTO",
                                "La clave ya fue usada con otros datos.");
                    }
                    PagoResponse pago = mapper.readValue(registro.getResponseJson(), PagoResponse.class);
                    return new ResultadoPago(pago, registro.getHttpStatus(), true);
                }).orElse(null);
    }

    public ResultadoPago ejecutarPago(Long actor, String operacion, UUID clave, String huella,
            Supplier<PagoResponse> accion) {
        ResultadoPago anterior = buscarPago(actor, operacion, clave, huella);
        if (anterior != null) {
            return anterior;
        }
        try {
            return transaccion.execute(status -> {
                SolicitudIdempotente registro = new SolicitudIdempotente();
                registro.setActorId(actor);
                registro.setOperacion(operacion);
                registro.setClave(clave);
                registro.setHuella(huella);
                solicitudes.saveAndFlush(registro);

                PagoResponse pago = accion.get();
                registro.setRecursoTipo("PAGO");
                registro.setRecursoId(pago.getId().toString());
                registro.setHttpStatus(200);
                registro.setResponseJson(mapper.writeValueAsString(pago));
                registro.setCompletadaEn(Instant.now());
                solicitudes.saveAndFlush(registro);
                return new ResultadoPago(pago, 200, false);
            });
        } catch (DataIntegrityViolationException ex) {
            // Leer el resultado ganador despues de terminar la transaccion fallida.
            ResultadoPago ganador = buscarPago(actor, operacion, clave, huella);
            if (ganador != null) {
                return ganador;
            }
            throw new NegocioException(409, "OPERACION_CONCURRENTE",
                    "La operacion cambio. Reintenta con la misma clave.");
        }
    }

    public static UUID clave(String valor) {
        try {
            UUID clave = UUID.fromString(valor);
            if (clave.toString().equalsIgnoreCase(valor)) {
                return clave;
            }
        } catch (IllegalArgumentException ex) {
            // La clave debe tener el formato UUID completo.
        }
        throw new NegocioException(400, "VALIDACION", "Idempotency-Key debe ser un UUID valido.");
    }

    public static String huella(String contenido) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(contenido.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible.");
        }
    }
}
