package com.fastfood.order.service;

import com.fastfood.order.dto.*;
import com.fastfood.order.event.OrderEventPublisher;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.model.*;
import com.fastfood.order.model.Pago.*;
import com.fastfood.order.model.PagoIntento.EstadoIntento;
import com.fastfood.order.model.Pedido.EstadoPedido;
import com.fastfood.order.repository.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final PedidoRepository pedidos;
    private final PagoRepository pagos;
    private final PagoIntentoRepository intentos;
    private final IdempotenciaService idempotencia;
    private final OrderEventPublisher eventos;

    @Value("${fastfood.pagos.yape.qr-url}")
    private String qrUrl;

    @Value("${fastfood.pagos.yape.nombre-comercio}")
    private String nombreComercio;

    @Value("${fastfood.pagos.yape.instrucciones}")
    private String instrucciones;

    public PagoConfiguracionResponse configuracion() {
        return new PagoConfiguracionResponse("PEN",
                new PagoConfiguracionResponse.Yape(qrUrl, nombreComercio, instrucciones),
                new PagoConfiguracionResponse.Efectivo(List.of(
                        new BigDecimal("50.00"), new BigDecimal("100.00"))));
    }

    @Transactional(readOnly = true)
    public PagoResponse obtener(Long id, Claims actor) {
        Pedido pedido = pedidos.findById(id).orElseThrow(this::noEncontrado);
        exigirAcceso(pedido, actor);
        return PagoResponse.de(pedido.getPago());
    }

    @Transactional(readOnly = true)
    public PaginaResponse<PagoIntentoResponse> listarIntentos(Long id, Claims actor, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new NegocioException(400, "VALIDACION", "La paginacion no es valida.");
        }
        Pedido pedido = pedidos.findById(id).orElseThrow(this::noEncontrado);
        exigirAcceso(pedido, actor);
        return PaginaResponse.de(intentos.findByPagoId(pedido.getPago().getId(),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id"))))
                .map(PagoIntentoResponse::de));
    }

    public ResultadoPago enviarYape(Long id, YapeEnviarRequest request, Claims actor, String clave) {
        String referencia = request.getReferenciaOperacion();
        String datos = referencia + "|" + request.getVersionPagoEsperada();
        return procesar(id, actor, clave, "YAPE_ENVIAR", datos, MetodoPago.YAPE,
                request.getVersionPagoEsperada(), false, pago -> {
                    exigirPagoDigital(pago);
                    PagoIntento intento = nuevoIntento(pago, referencia, EstadoIntento.PENDIENTE_VALIDACION);
                    pago.setEstadoPago(EstadoPago.PENDIENTE_VALIDACION);
                    pago.setIntentoActualId(intento.getId());
                    pago.setReferenciaOperacion(referencia);
                });
    }

    public ResultadoPago resolverYape(Long id, YapeResolverRequest request, Claims actor, String clave) {
        String motivo = request.getMotivo() == null ? "" : request.getMotivo().trim();
        if (!request.getAprobar() && motivo.isEmpty()) {
            throw new NegocioException(400, "VALIDACION", "Indica el motivo del rechazo.");
        }
        String datos = request.getIntentoId() + "|" + request.getAprobar() + "|" + motivo
                + "|" + request.getVersionPagoEsperada();
        return procesar(id, actor, clave, "YAPE_RESOLVER", datos, MetodoPago.YAPE,
                request.getVersionPagoEsperada(), true, pago -> {
                    if (pago.getPedido().getEstadoPedido() != EstadoPedido.REGISTRADO
                            || pago.getEstadoPago() != EstadoPago.PENDIENTE_VALIDACION
                            || !Objects.equals(pago.getIntentoActualId(), request.getIntentoId())) {
                        throw conflicto("El intento ya no permite validacion.");
                    }
                    PagoIntento intento = intentos.findById(request.getIntentoId())
                            .orElseThrow(this::noEncontrado);
                    if (!intento.getPago().getId().equals(pago.getId())
                            || intento.getEstado() != EstadoIntento.PENDIENTE_VALIDACION) {
                        throw conflicto("El intento no corresponde al pago pendiente.");
                    }
                    resolver(pago, intento, request.getAprobar(), motivo, actorId(actor));
                });
    }

    public ResultadoPago procesarTarjeta(Long id, TarjetaProcesarRequest request, Claims actor, String clave) {
        boolean aprobado = validarTarjeta(request);
        // La huella solo incluye el escenario y la version; nunca datos de tarjeta.
        String datos = request.getVersionPagoEsperada() + "|" + (aprobado ? "APROBACION" : "RECHAZO");
        return procesar(id, actor, clave, "TARJETA", datos, MetodoPago.TARJETA,
                request.getVersionPagoEsperada(), false, pago -> {
                    exigirPagoDigital(pago);
                    PagoIntento intento = nuevoIntento(pago, "TAR-" + UUID.randomUUID(),
                            aprobado ? EstadoIntento.PAGADO : EstadoIntento.RECHAZADO);
                    resolver(pago, intento, aprobado, aprobado ? null : "Pago rechazado", actorId(actor));
                    pago.setIntentoActualId(intento.getId());
                    pago.setReferenciaOperacion(intento.getReferenciaOperacion());
                });
    }

    public ResultadoPago confirmarEfectivo(Long id, EfectivoConfirmarRequest request, Claims actor, String clave) {
        String datos = request.getMontoRecibido().setScale(2).toPlainString()
                + "|" + request.getVersionPagoEsperada();
        return procesar(id, actor, clave, "EFECTIVO", datos, MetodoPago.EFECTIVO,
                request.getVersionPagoEsperada(), true, pago -> {
                    if (pago.getPedido().getEstadoPedido() != EstadoPedido.LISTO
                            || pago.getEstadoPago() != EstadoPago.PENDIENTE) {
                        throw conflicto("El cobro en efectivo requiere un pedido listo y pago pendiente.");
                    }
                    if (request.getMontoRecibido().compareTo(pago.getMonto()) < 0) {
                        throw new NegocioException(400, "VALIDACION", "El monto recibido debe cubrir el total.");
                    }
                    PagoIntento intento = nuevoIntento(pago, "EF-" + UUID.randomUUID(), EstadoIntento.PAGADO);
                    resolver(pago, intento, true, null, actorId(actor));
                    pago.setMontoRecibido(request.getMontoRecibido().setScale(2));
                    pago.setVueltoFinal(request.getMontoRecibido().subtract(pago.getMonto()).setScale(2));
                    pago.setIntentoActualId(intento.getId());
                    pago.setReferenciaOperacion(intento.getReferenciaOperacion());
                });
    }

    private ResultadoPago procesar(Long id, Claims actor, String claveHeader, String accion, String datos,
            MetodoPago metodo, Long version, boolean personal, Consumer<Pago> cambio) {
        exigirRol(actor, personal);
        UUID clave = IdempotenciaService.clave(claveHeader);
        String operacion = accion + ":" + id;
        String huella = IdempotenciaService.huella(id + "|" + metodo + "|" + datos);
        ResultadoPago resultado = idempotencia.ejecutarPago(actorId(actor), operacion, clave, huella, () -> {
            // Todas las escrituras bloquean primero Pedido y despues Pago.
            Pedido pedido = pedidos.obtenerConBloqueo(id).orElseThrow(this::noEncontrado);
            exigirAcceso(pedido, actor);
            Pago pago = pagos.obtenerConBloqueo(id).orElseThrow(this::noEncontrado);
            if (pago.getMetodoPago() != metodo) {
                throw conflicto("El metodo de pago no corresponde al pedido.");
            }
            if (pago.getEstadoPago() == EstadoPago.PAGADO) {
                throw new NegocioException(409, "PAGO_YA_CONFIRMADO", "El pedido ya esta pagado.");
            }
            if (!Objects.equals(pago.getVersion(), version)) {
                throw new NegocioException(409, "VERSION_DESACTUALIZADA",
                        "El pago fue modificado. Actualiza los datos.");
            }
            cambio.accept(pago);
            pagos.saveAndFlush(pago);
            return PagoResponse.de(pago);
        });
        if (!resultado.isReplayed()) {
            try {
                eventos.publicar("PEDIDO_ACTUALIZADO", id);
            } catch (RuntimeException ex) {
                log.warn("No se publico la actualizacion del pedido {} ({})", id, ex.getClass().getSimpleName());
            }
        }
        return resultado;
    }

    private PagoIntento nuevoIntento(Pago pago, String referencia, EstadoIntento estado) {
        PagoIntento intento = new PagoIntento();
        intento.setPago(pago);
        intento.setNumeroIntento(Math.toIntExact(intentos.countByPagoId(pago.getId()) + 1));
        intento.setEstado(estado);
        intento.setReferenciaOperacion(referencia);
        intento.setCreadoEn(Instant.now());
        return intentos.saveAndFlush(intento);
    }

    private void resolver(Pago pago, PagoIntento intento, boolean aprobado, String motivo, Long actor) {
        Instant ahora = Instant.now();
        intento.setEstado(aprobado ? EstadoIntento.PAGADO : EstadoIntento.RECHAZADO);
        intento.setMotivoRechazo(aprobado ? null : motivo);
        intento.setResueltoEn(ahora);
        intento.setResueltoPorId(actor);
        pago.setEstadoPago(aprobado ? EstadoPago.PAGADO : EstadoPago.RECHAZADO);
        pago.setFechaPago(aprobado ? ahora : null);
    }

    private void exigirPagoDigital(Pago pago) {
        if (pago.getPedido().getEstadoPedido() != EstadoPedido.REGISTRADO
                || pago.getEstadoPago() != EstadoPago.PENDIENTE && pago.getEstadoPago() != EstadoPago.RECHAZADO) {
            throw conflicto("El pedido ya no permite enviar un pago.");
        }
    }

    private boolean validarTarjeta(TarjetaProcesarRequest request) {
        String numero = request.getNumeroTarjeta().replace(" ", "").replace("-", "");
        if (!numero.equals("4111111111111111") && !numero.equals("4000000000000002")) {
            throw new NegocioException(400, "TARJETA_PRUEBA_INVALIDA", "No se pudo procesar esta tarjeta. Verifica los datos ingresados.");
        }
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        int anio = request.getAnioVencimiento();
        if (anio < hoy.getYear() || anio > hoy.getYear() + 15
                || YearMonth.of(anio, request.getMesVencimiento()).atEndOfMonth().isBefore(hoy)) {
            throw new NegocioException(400, "VALIDACION", "El vencimiento de la tarjeta no es valido.");
        }
        return numero.equals("4111111111111111");
    }

    private void exigirAcceso(Pedido pedido, Claims actor) {
        String rol = actor.get("rol", String.class);
        if ("CLIENTE".equals(rol)) {
            if (!pedido.getClienteId().equals(actorId(actor))) {
                throw new NegocioException(403, "ACCESO_DENEGADO", "El pedido pertenece a otro cliente.");
            }
        } else {
            exigirRol(actor, true);
        }
    }

    private void exigirRol(Claims actor, boolean personal) {
        String rol = actor.get("rol", String.class);
        boolean permitido = personal ? Set.of("EMPLEADO", "ADMINISTRADOR").contains(rol) : "CLIENTE".equals(rol);
        if (!permitido) {
            throw new NegocioException(403, "ACCESO_DENEGADO", "No tienes permiso para esta operacion.");
        }
    }

    private Long actorId(Claims actor) {
        return ((Number) actor.get("usuarioId")).longValue();
    }

    private NegocioException conflicto(String mensaje) {
        return new NegocioException(409, "TRANSICION_INVALIDA", mensaje);
    }

    private NegocioException noEncontrado() {
        return new NegocioException(404, "RECURSO_NO_ENCONTRADO", "El recurso solicitado no existe.");
    }
}
