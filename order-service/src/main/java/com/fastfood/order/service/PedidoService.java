package com.fastfood.order.service;

import com.fastfood.order.dto.*;
import com.fastfood.order.event.OrderEventPublisher;
import com.fastfood.order.exception.*;
import com.fastfood.order.feign.AuthEstadoFeignClient;
import com.fastfood.order.model.*;
import com.fastfood.order.model.Pago.*;
import com.fastfood.order.model.Pedido.EstadoPedido;
import com.fastfood.order.repository.*;
import com.fastfood.order.security.JwtUtil;

import io.jsonwebtoken.Claims;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidos;
    private final CotizacionRepository cotizaciones;
    private final PagoRepository pagos;
    private final PagoIntentoRepository intentos;
    private final ConversacionRepository conversaciones;
    private final PedidoCambioEstadoRepository cambios;
    private final CotizacionService cotizacionService;
    private final IdempotenciaService idempotencia;
    private final AuthEstadoFeignClient auth;
    private final JwtUtil jwt;
    private final PlatformTransactionManager manager;
    private final OrderEventPublisher eventos;

    public ResultadoPedido crear(PedidoCrearRequest request, Claims actor, String claveHeader) {
        Long clienteId = actorId(actor);
        UUID clave = IdempotenciaService.clave(claveHeader);
        String huella =
                IdempotenciaService.huella(
                        request.getCotizacionId()
                                + "|"
                                + request.getMetodoPago()
                                + "|"
                                + (request.getMontoAnunciado() == null
                                        ? ""
                                        : request.getMontoAnunciado().setScale(2).toPlainString()));
        ResultadoPedido replay = idempotencia.buscar(clienteId, "CREAR_PEDIDO", clave, huella);
        if (replay != null) {
            return replay;
        }

        Cotizacion cotizacion =
                cotizacionService.obtenerParaRegistro(request.getCotizacionId(), clienteId);
        ResultadoPedido existente =
                new TransactionTemplate(manager)
                        .execute(
                                status ->
                                        pedidos.findByCotizacionId(cotizacion.getId())
                                                .map(p -> recuperarExistente(p, request, clienteId))
                                                .orElse(null));
        if (existente != null) {
            return idempotencia.ejecutar(clienteId, "CREAR_PEDIDO", clave, huella, () -> existente);
        }
        CotizacionService.exigirVigencia(cotizacion);
        List<Long> ids =
                cotizacion.getDetalles().stream()
                        .map(CotizacionDetalle::getProductoId)
                        .distinct()
                        .toList();
        try {
            var oferta = cotizacionService.consultarCatalogo(ids);
            if (!cotizacion.getHuellaCatalogo().equals(CotizacionService.huellaCatalogo(oferta))) {
                throw catalogoCambio();
            }
        } catch (RemoteBusinessException ex) {
            if (ex.getStatus() == 409) {
                throw catalogoCambio();
            }
            throw ex;
        }
        validarMonto(request, cotizacion.getTotal());
        UsuarioEstadoResponse cliente =
                auth.obtener(clienteId, "Bearer " + jwt.generarTecnicoAuth());
        validarCliente(cliente, actor);

        ResultadoPedido resultado =
                idempotencia.ejecutar(
                        clienteId,
                        "CREAR_PEDIDO",
                        clave,
                        huella,
                        () -> {
                            Cotizacion bloqueada =
                                    cotizaciones
                                            .obtenerConBloqueo(request.getCotizacionId())
                                            .orElseThrow(this::noEncontrado);
                            CotizacionService.exigirPropiedad(bloqueada, clienteId);
                            var anterior = pedidos.findByCotizacionId(bloqueada.getId());
                            if (anterior.isPresent()) {
                                return recuperarExistente(anterior.get(), request, clienteId);
                            }
                            CotizacionService.exigirVigencia(bloqueada);
                            if (bloqueada.getEstado() != Cotizacion.EstadoCotizacion.VIGENTE) {
                                throw new NegocioException(
                                        409,
                                        "TRANSICION_INVALIDA",
                                        "La cotizacion ya fue utilizada.");
                            }
                            Pedido pedido = construir(bloqueada, request, cliente);
                            pedidos.saveAndFlush(pedido);
                            registrarCambio(pedido, null, EstadoPedido.REGISTRADO, clienteId, null);
                            bloqueada.setEstado(Cotizacion.EstadoCotizacion.UTILIZADA);
                            cotizaciones.saveAndFlush(bloqueada);
                            return new ResultadoPedido(
                                    PedidoDetalleResponse.de(pedido), 201, false);
                        });
        if (!resultado.isReplayed() && resultado.getStatus() == 201) {
            publicar("PEDIDO_NUEVO", resultado.getPedido().getId());
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public PedidoDetalleResponse obtener(Long id, Claims actor) {
        Pedido pedido = buscar(id);
        exigirAcceso(pedido, actor);
        return PedidoDetalleResponse.de(pedido);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<PedidoResumenResponse> listar(
            boolean propios,
            Claims actor,
            EstadoPedido estado,
            EstadoPago estadoPago,
            MetodoPago metodoPago,
            Long clienteId,
            LocalDate desde,
            LocalDate hasta,
            int page,
            int size) {
        return listar(propios, actor, estado, estadoPago, metodoPago, clienteId,
                null, null, desde, hasta, page, size);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<PedidoResumenResponse> listar(
            boolean propios, Claims actor, EstadoPedido estado, EstadoPago estadoPago,
            MetodoPago metodoPago, Long clienteId, String clienteBusqueda, String numeroPedido,
            LocalDate desde, LocalDate hasta, int page, int size) {
        validarFiltros(page, size, desde, hasta);
        if (clienteId != null && clienteId <= 0) {
            throw new NegocioException(400, "VALIDACION", "El cliente debe ser un ID positivo.");
        }
        Long cliente = propios ? actorId(actor) : clienteId;
        Specification<Pedido> filtro = (root, query, cb) -> cb.conjunction();
        if (clienteBusqueda != null && !clienteBusqueda.isBlank()) {
            String texto = clienteBusqueda.trim().toLowerCase(java.util.Locale.ROOT);
            if (texto.length() > 201) {
                throw new NegocioException(400, "VALIDACION", "La búsqueda del cliente admite hasta 201 caracteres.");
            }
            String patron = "%" + texto.replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%";
            filtro = filtro.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("clienteNombres")), patron, '!'),
                    cb.like(cb.lower(root.get("clienteCorreo")), patron, '!')));
        }
        if (numeroPedido != null && !numeroPedido.isBlank()) {
            String numero = numeroPedido.trim().toUpperCase(java.util.Locale.ROOT);
            Long pedidoId;
            try {
                if (!numero.matches("FF-[0-9]{6,19}")) throw new NumberFormatException();
                pedidoId = Long.valueOf(numero.substring(3));
                if (pedidoId <= 0 || !numero.equals(String.format(java.util.Locale.ROOT, "FF-%06d", pedidoId))) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                throw new NegocioException(400, "VALIDACION", "Ingresa un número de pedido válido, por ejemplo FF-000033.");
            }
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("id"), pedidoId));
        }
        if (cliente != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("clienteId"), cliente));
        }
        if (estado != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estadoPedido"), estado));
        }
        if (estadoPago != null) {
            filtro =
                    filtro.and(
                            (root, query, cb) ->
                                    cb.equal(root.get("pago").get("estadoPago"), estadoPago));
        }
        if (metodoPago != null) {
            filtro =
                    filtro.and(
                            (root, query, cb) ->
                                    cb.equal(root.get("pago").get("metodoPago"), metodoPago));
        }
        if (desde != null) {
            Instant inicio = desde.atStartOfDay(ZoneId.of("America/Lima")).toInstant();
            filtro =
                    filtro.and(
                            (root, query, cb) ->
                                    cb.greaterThanOrEqualTo(root.get("fechaCreacion"), inicio));
        }
        if (hasta != null) {
            Instant fin = hasta.plusDays(1).atStartOfDay(ZoneId.of("America/Lima")).toInstant();
            filtro = filtro.and((root, query, cb) -> cb.lessThan(root.get("fechaCreacion"), fin));
        }
        Page<Pedido> pagina =
                pedidos.findAll(
                        filtro,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Order.desc("fechaCreacion"), Sort.Order.desc("id"))));
        return PaginaResponse.de(pagina.map(PedidoResumenResponse::de));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<PedidoCambioEstadoResponse> historial(
            Long id, Claims actor, int page, int size) {
        validarFiltros(page, size, null, null);
        Pedido pedido = buscar(id);
        exigirAcceso(pedido, actor);
        return PaginaResponse.de(
                cambios.findByPedidoId(id, PageRequest.of(page, size, Sort.by("fecha", "id")))
                        .map(PedidoCambioEstadoResponse::de));
    }

    public ResultadoPedido transicionar(
            Long id, PedidoTransicionRequest request, Claims actor, String claveHeader) {
        Long actorId = actorId(actor);
        UUID clave = IdempotenciaService.clave(claveHeader);
        String operacion = "ESTADO:" + id;
        String huella =
                IdempotenciaService.huella(
                        id + "|" + request.getEstadoDestino() + "|" + request.getVersionEsperada());
        ResultadoPedido resultado =
                idempotencia.ejecutar(
                        actorId,
                        operacion,
                        clave,
                        huella,
                        () -> {
                            Pedido pedido = bloquear(id);
                            comprobarVersion(pedido, request.getVersionEsperada());
                            Pago pago = pagos.obtenerConBloqueo(id).orElseThrow(this::noEncontrado);
                            EstadoPedido anterior = pedido.getEstadoPedido();
                            EstadoPedido destino = request.getEstadoDestino();
                            validarTransicion(anterior, destino, pago);
                            pedido.setEstadoPedido(destino);
                            if (destino == EstadoPedido.ENTREGADO) {
                                cerrarConversacion(id);
                            }
                            registrarCambio(pedido, anterior, destino, actorId, null);
                            pedidos.saveAndFlush(pedido);
                            return new ResultadoPedido(
                                    PedidoDetalleResponse.de(pedido), 200, false);
                        });
        if (!resultado.isReplayed()) {
            publicar("PEDIDO_ACTUALIZADO", id);
            if (resultado.getPedido().getEstadoPedido() == EstadoPedido.ENTREGADO) {
                publicar("CHAT_CERRADO", id);
            }
        }
        return resultado;
    }

    public ResultadoPedido cancelar(
            Long id, PedidoCancelarRequest request, Claims actor, String claveHeader) {
        Long actorId = actorId(actor);
        UUID clave = IdempotenciaService.clave(claveHeader);
        String motivo = request.getMotivo().trim();
        String operacion = "CANCELAR:" + id;
        String huella =
                IdempotenciaService.huella(id + "|" + request.getVersionEsperada() + "|" + motivo);
        ResultadoPedido resultado =
                idempotencia.ejecutar(
                        actorId,
                        operacion,
                        clave,
                        huella,
                        () -> {
                            Pedido pedido = bloquear(id);
                            comprobarVersion(pedido, request.getVersionEsperada());
                            Pago pago = pagos.obtenerConBloqueo(id).orElseThrow(this::noEncontrado);
                            EstadoPedido anterior = pedido.getEstadoPedido();
                            if ((anterior != EstadoPedido.REGISTRADO
                                            && anterior != EstadoPedido.CONFIRMADO)
                                    || pago.getEstadoPago() == EstadoPago.PAGADO) {
                                throw new NegocioException(
                                        409,
                                        "TRANSICION_INVALIDA",
                                        "El pedido no permite cancelacion.");
                            }
                            pedido.setEstadoPedido(EstadoPedido.CANCELADO);
                            pedido.setMotivoCancelacion(motivo);
                            pago.setEstadoPago(EstadoPago.ANULADO);
                            if (pago.getIntentoActualId() != null) {
                                PagoIntento intento = intentos.findById(pago.getIntentoActualId())
                                        .orElseThrow(this::noEncontrado);
                                if (intento.getEstado() == PagoIntento.EstadoIntento.PENDIENTE_VALIDACION) {
                                    intento.setEstado(PagoIntento.EstadoIntento.ANULADO);
                                    intento.setResueltoEn(Instant.now());
                                    intento.setResueltoPorId(actorId);
                                }
                            }
                            pagos.saveAndFlush(pago);
                            cerrarConversacion(id);
                            registrarCambio(
                                    pedido, anterior, EstadoPedido.CANCELADO, actorId, motivo);
                            pedidos.saveAndFlush(pedido);
                            return new ResultadoPedido(
                                    PedidoDetalleResponse.de(pedido), 200, false);
                        });
        if (!resultado.isReplayed()) {
            publicar("PEDIDO_ACTUALIZADO", id);
            publicar("CHAT_CERRADO", id);
        }
        return resultado;
    }

    private Pedido construir(
            Cotizacion cotizacion, PedidoCrearRequest request, UsuarioEstadoResponse cliente) {
        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNombres((cliente.getNombres() + " " + cliente.getApellidos()).trim());
        pedido.setClienteCorreo(cliente.getCorreo());
        pedido.setClienteTelefono(cliente.getTelefono());
        pedido.setCotizacionId(cotizacion.getId());
        pedido.setTotal(cotizacion.getTotal());
        for (CotizacionDetalle linea : cotizacion.getDetalles()) {
            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProductoId(linea.getProductoId());
            detalle.setCategoriaId(linea.getCategoriaId());
            detalle.setNombreProducto(linea.getNombreProducto());
            detalle.setPrecioUnitario(linea.getPrecioUnitario());
            detalle.setCantidad(linea.getCantidad());
            detalle.setSubtotal(linea.getSubtotal());
            detalle.setObservacion(linea.getObservacion());
            pedido.getDetalles().add(detalle);
        }
        Pago pago = new Pago();
        pago.setPedido(pedido);
        pago.setMetodoPago(request.getMetodoPago());
        pago.setMonto(pedido.getTotal());
        if (request.getMetodoPago() == MetodoPago.EFECTIVO) {
            pago.setMontoAnunciado(request.getMontoAnunciado().setScale(2));
            pago.setVueltoEstimado(
                    request.getMontoAnunciado().subtract(pedido.getTotal()).setScale(2));
        }
        pedido.setPago(pago);
        Conversacion conversacion = new Conversacion();
        conversacion.setPedido(pedido);
        conversacion.setClienteId(pedido.getClienteId());
        conversacion.setFechaInicio(Instant.now());
        pedido.setConversacion(conversacion);
        return pedido;
    }

    private ResultadoPedido recuperarExistente(
            Pedido pedido, PedidoCrearRequest request, Long clienteId) {
        if (!pedido.getClienteId().equals(clienteId)) {
            throw new NegocioException(
                    403, "ACCESO_DENEGADO", "El pedido pertenece a otro cliente.");
        }
        BigDecimal anunciado = pedido.getPago().getMontoAnunciado();
        boolean coincide =
                anunciado == null
                        ? request.getMontoAnunciado() == null
                        : request.getMontoAnunciado() != null
                                && anunciado.compareTo(request.getMontoAnunciado()) == 0;
        if (pedido.getPago().getMetodoPago() != request.getMetodoPago() || !coincide) {
            throw new NegocioException(
                    409,
                    "IDEMPOTENCIA_CONFLICTO",
                    "La cotizacion ya produjo un pedido con otros datos.");
        }
        return new ResultadoPedido(PedidoDetalleResponse.de(pedido), 200, false);
    }

    private void validarMonto(PedidoCrearRequest request, BigDecimal total) {
        if (request.getMetodoPago() == MetodoPago.EFECTIVO) {
            if (request.getMontoAnunciado() == null
                    || request.getMontoAnunciado().compareTo(total) < 0) {
                throw new NegocioException(
                        400, "VALIDACION", "El monto anunciado debe cubrir el total.");
            }
        } else if (request.getMontoAnunciado() != null) {
            throw new NegocioException(
                    400, "VALIDACION", "El monto anunciado corresponde solo a efectivo.");
        }
    }

    private void validarCliente(UsuarioEstadoResponse cliente, Claims actor) {
        if (cliente == null
                || cliente.getNombres() == null
                || cliente.getNombres().isBlank()
                || cliente.getNombres().length() > 100
                || cliente.getApellidos() == null
                || cliente.getApellidos().isBlank()
                || cliente.getApellidos().length() > 100
                || cliente.getTelefono() == null
                || cliente.getTelefono().isBlank()) {
            throw new NegocioException(
                    503, "DEPENDENCIA_NO_DISPONIBLE", "La respuesta de Auth no es valida.");
        }
        if (!actorId(actor).equals(cliente.getId())
                || !"ACTIVO".equals(cliente.getEstado())
                || !"CLIENTE".equals(cliente.getRol())
                || !Objects.equals(cliente.getCorreo(), actor.get("correo"))
                || !Objects.equals(
                        cliente.getVersionSeguridad(),
                        ((Number) actor.get("versionSeguridad")).longValue())) {
            throw new NegocioException(401, "SESION_VENCIDA", "La sesion ya no es valida.");
        }
    }

    private void validarTransicion(EstadoPedido anterior, EstadoPedido destino, Pago pago) {
        boolean valida =
                anterior == EstadoPedido.REGISTRADO && destino == EstadoPedido.CONFIRMADO
                        || anterior == EstadoPedido.CONFIRMADO
                                && destino == EstadoPedido.EN_PREPARACION
                        || anterior == EstadoPedido.EN_PREPARACION && destino == EstadoPedido.LISTO
                        || anterior == EstadoPedido.LISTO && destino == EstadoPedido.ENTREGADO;
        if (!valida) {
            throw new NegocioException(
                    409, "TRANSICION_INVALIDA", "La transicion del pedido no esta permitida.");
        }
        if (destino == EstadoPedido.CONFIRMADO || destino == EstadoPedido.EN_PREPARACION) {
            boolean permitePreparar =
                    pago.getEstadoPago() == EstadoPago.PAGADO
                            || pago.getMetodoPago() == MetodoPago.EFECTIVO
                                    && pago.getEstadoPago() == EstadoPago.PENDIENTE;
            if (!permitePreparar) {
                throw new NegocioException(
                        409, "TRANSICION_INVALIDA", "El pago no permite preparar el pedido.");
            }
        }
        if (destino == EstadoPedido.ENTREGADO && pago.getEstadoPago() != EstadoPago.PAGADO) {
            throw new NegocioException(
                    409, "TRANSICION_INVALIDA", "El pedido debe estar pagado antes de entregarlo.");
        }
    }

    private void cerrarConversacion(Long pedidoId) {
        Conversacion conversacion =
                conversaciones.obtenerConBloqueo(pedidoId).orElseThrow(this::noEncontrado);
        conversacion.setEstado(Conversacion.EstadoConversacion.CERRADA);
        conversacion.setFechaCierre(Instant.now());
    }

    private void registrarCambio(
            Pedido pedido, EstadoPedido anterior, EstadoPedido nuevo, Long actor, String motivo) {
        PedidoCambioEstado cambio = new PedidoCambioEstado();
        cambio.setPedido(pedido);
        cambio.setAnterior(anterior);
        cambio.setNuevo(nuevo);
        cambio.setActorId(actor);
        cambio.setMotivo(motivo);
        cambio.setFecha(Instant.now());
        cambios.save(cambio);
    }

    private Pedido buscar(Long id) {
        return pedidos.findById(id).orElseThrow(this::noEncontrado);
    }

    private Pedido bloquear(Long id) {
        return pedidos.obtenerConBloqueo(id).orElseThrow(this::noEncontrado);
    }

    private void comprobarVersion(Pedido pedido, Long version) {
        if (!Objects.equals(pedido.getVersion(), version)) {
            throw new NegocioException(
                    409,
                    "VERSION_DESACTUALIZADA",
                    "El pedido fue modificado. Actualiza los datos.");
        }
    }

    private void exigirAcceso(Pedido pedido, Claims actor) {
        if ("CLIENTE".equals(actor.get("rol", String.class))
                && !pedido.getClienteId().equals(actorId(actor))) {
            throw new NegocioException(
                    403, "ACCESO_DENEGADO", "El pedido pertenece a otro cliente.");
        }
    }

    private Long actorId(Claims actor) {
        return ((Number) actor.get("usuarioId")).longValue();
    }

    private void validarFiltros(int page, int size, LocalDate desde, LocalDate hasta) {
        if (page < 0
                || size < 1
                || size > 100
                || desde != null && hasta != null && desde.isAfter(hasta)
                || desde != null && (desde.getYear() < 1 || desde.getYear() > 9998)
                || hasta != null && (hasta.getYear() < 1 || hasta.getYear() > 9998)) {
            throw new NegocioException(
                    400, "RANGO_FECHAS_INVALIDO", "Los filtros o la paginacion no son validos.");
        }
    }

    private NegocioException noEncontrado() {
        return new NegocioException(
                404, "RECURSO_NO_ENCONTRADO", "El recurso solicitado no existe.");
    }

    private NegocioException catalogoCambio() {
        return new NegocioException(
                409, "CATALOGO_CAMBIO", "El catalogo cambio. Vuelve a cotizar.");
    }

    private void publicar(String tipo, Long pedidoId) {
        try {
            eventos.publicar(tipo, pedidoId);
        } catch (RuntimeException ex) {
            log.warn(
                    "No se publico {} para pedido {} ({})",
                    tipo,
                    pedidoId,
                    ex.getClass().getSimpleName());
        }
    }
}
