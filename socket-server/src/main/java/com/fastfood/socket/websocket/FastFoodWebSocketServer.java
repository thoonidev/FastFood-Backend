package com.fastfood.socket.websocket;

import com.fastfood.socket.dto.*;
import com.fastfood.socket.exception.NegocioException;
import com.fastfood.socket.feign.OrderChatFeignClient;
import com.fastfood.socket.service.SocketAuthService;
import feign.FeignException;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.DefaultExtension;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class FastFoodWebSocketServer extends WebSocketServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(FastFoodWebSocketServer.class);
    private final SocketAuthService auth;
    private final OrderChatFeignClient order;
    private final JsonMapper mapper;
    private final String origin;
    private final ConcurrentHashMap<WebSocket, SocketSession> sesiones = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor trabajadores = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200));
    private final Semaphore cupoTrabajo = new Semaphore(200);
    private final ScheduledExecutorService reloj = Executors.newSingleThreadScheduledExecutor();
    private final LinkedHashMap<UUID, Instant> eventos = new LinkedHashMap<>();
    private volatile boolean iniciado;
    private volatile boolean listo;

    public FastFoodWebSocketServer(@Value("${fastfood.socket.port}") int port,
            @Value("${fastfood.cors.allowed-origin}") String origin,
            SocketAuthService auth, OrderChatFeignClient order, JsonMapper mapper) {
        super(new InetSocketAddress(port), 2,
                List.of(new Draft_6455(List.of(new DefaultExtension()), List.of(new Protocol("")), 8192)));
        this.auth = auth;
        this.order = order;
        this.mapper = mapper;
        this.origin = origin;
        setReuseAddr(true);
        setConnectionLostTimeout(30);
    }

    @Override
    public synchronized void start() {
        if (!iniciado) {
            iniciado = true;
            super.start();
            reloj.scheduleAtFixedRate(this::revisarSesiones, 1, 1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void stop() {
        iniciado = false;
        listo = false;
        reloj.shutdownNow();
        sesiones.keySet().forEach(conexion -> conexion.close(1001, "Servidor detenido"));
        try {
            super.stop(1000);
            trabajadores.shutdown();
            if (!trabajadores.awaitTermination(4, TimeUnit.SECONDS)) {
                trabajadores.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            trabajadores.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return iniciado;
    }

    public boolean estaListo() {
        return listo;
    }

    @Override
    public void onStart() {
        listo = true;
        log.info("WebSocket disponible en puerto {}", getPort());
    }

    @Override
    public void onOpen(WebSocket conexion, ClientHandshake handshake) {
        if (!origin.equals(handshake.getFieldValue("Origin")) || !"/".equals(handshake.getResourceDescriptor())) {
            conexion.close(1008, "Origen o ruta no permitido");
            return;
        }
        synchronized (sesiones) {
            if (sesiones.size() >= 50) {
                conexion.close(1013, "Limite de conexiones");
                return;
            }
            sesiones.put(conexion, new SocketSession(conexion));
        }
    }

    @Override
    public void onMessage(WebSocket conexion, String texto) {
        if (texto.getBytes(StandardCharsets.UTF_8).length > 8192) {
            conexion.close(1009, "Mensaje demasiado grande");
            return;
        }
        SocketSession sesion = sesiones.get(conexion);
        if (sesion == null) {
            conexion.close(1008, "Sesion no disponible");
            return;
        }
        synchronized (sesion) {
            if (sesion.isCerrada()) {
                return;
            }
            Instant ahora = Instant.now();
            while (!sesion.getComandos().isEmpty() && sesion.getComandos().peek().isBefore(ahora.minusSeconds(60))) {
                sesion.getComandos().poll();
            }
            if (sesion.getComandos().size() >= 60 || !cupoTrabajo.tryAcquire()) {
                conexion.close(1013, "Limite de comandos o cola saturada");
                return;
            }
            sesion.getComandos().add(ahora);
            sesion.getPendientes().add(texto);
            if (!sesion.isAtendiendo()) {
                sesion.setAtendiendo(true);
                try {
                    trabajadores.execute(() -> atender(sesion));
                } catch (RejectedExecutionException ex) {
                    descartar(sesion);
                    conexion.close(1013, "Servidor ocupado");
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket conexion, ByteBuffer bytes) {
        conexion.close(bytes.remaining() > 8192 ? 1009 : 1008, "Solo mensajes JSON de texto");
    }

    private void atender(SocketSession sesion) {
        while (true) {
            String texto;
            synchronized (sesion) {
                texto = sesion.getPendientes().poll();
                if (texto == null) {
                    sesion.setAtendiendo(false);
                    return;
                }
            }
            try {
                if (sesion.getConexion().isOpen()) {
                    ejecutar(sesion, texto);
                }
            } finally {
                cupoTrabajo.release();
            }
        }
    }

    private void ejecutar(SocketSession sesion, String texto) {
        SocketEnvelope comando = correlacion(texto);
        try {
            comando = leerComando(texto);
            String tipo = comando.getTipo();
            if (sesion.getToken() == null) {
                if (!"AUTH".equals(tipo) || Instant.now().isAfter(sesion.getDeadline())) {
                    throw new NegocioException(401, "SESION_INVALIDA", "AUTH es el primer comando requerido.");
                }
                autenticar(sesion, comando);
                return;
            }
            if ("AUTH".equals(tipo)) {
                throw new NegocioException(400, "PROTOCOLO_INVALIDO", "La conexion ya esta autenticada.");
            }
            if ("PING".equals(tipo)) {
                auth.validarToken(sesion.getToken());
                responder(sesion, comando, "PONG", Map.of());
                return;
            }
            sesion.setUsuario(auth.validar(sesion.getToken()));
            Long id = comando.getIdPedido();
            switch (tipo) {
                case "SUBSCRIBIR_PEDIDO" -> {
                    var contexto = consultarAcceso(id, sesion);
                    sesion.getSuscripciones().add(id);
                    responder(sesion, comando, "SUSCRIPCION_OK", contexto);
                }
                case "DESUSCRIBIR_PEDIDO" -> {
                    sesion.getSuscripciones().remove(id);
                    responder(sesion, comando, "ACK", Map.of());
                }
                case "ENVIAR_MENSAJE" -> {
                    if (!sesion.getSuscripciones().contains(id)) {
                        throw new NegocioException(403, "ACCESO_DENEGADO", "Suscribete al pedido antes de enviar.");
                    }
                    consultarAcceso(id, sesion);
                    var mensaje = order.enviar(id, comando.getPayload(), "Bearer " + sesion.getToken());
                    validarMensaje(mensaje);
                    responder(sesion, comando, "MENSAJE_ACEPTADO", mensaje);
                }
                default -> throw protocolo();
            }
        } catch (NegocioException ex) {
            error(sesion, comando, ex.getStatus(), ex.getCode(), ex.getMessage());
            if (ex.getStatus() == 401 || ex.getStatus() == 403) {
                sesion.getConexion().close(1008, "Sesion invalida");
            }
        } catch (FeignException ex) {
            errorRemoto(sesion, comando, ex);
        } catch (RuntimeException ex) {
            error(sesion, comando, 400, "PROTOCOLO_INVALIDO", "El comando no es valido.");
        }
    }

    private SocketEnvelope leerComando(String texto) {
        var json = mapper.readTree(texto);
        Set<String> campos = Set.of("version", "tipo", "requestId", "idPedido", "payload");
        if (!json.isObject() || !json.path("version").isIntegralNumber() || json.path("version").asInt() != 1
                || !json.path("tipo").isString() || !json.path("payload").isObject()) {
            throw protocolo();
        }
        for (var campo : json.properties()) {
            if (!campos.contains(campo.getKey())) {
                throw protocolo();
            }
        }
        uuid(json.path("requestId").asString());
        SocketEnvelope comando = mapper.treeToValue(json, SocketEnvelope.class);
        Set<String> payloadPermitido = switch (comando.getTipo()) {
            case "AUTH" -> Set.of("token");
            case "SUBSCRIBIR_PEDIDO", "DESUSCRIBIR_PEDIDO", "PING" -> Set.of();
            case "ENVIAR_MENSAJE" -> Set.of("clientMessageId", "contenido");
            default -> throw protocolo();
        };
        if (!comando.getPayload().keySet().equals(payloadPermitido)) {
            throw protocolo();
        }
        if ("AUTH".equals(comando.getTipo()) || "PING".equals(comando.getTipo())) {
            if (json.has("idPedido")) {
                throw protocolo();
            }
        } else if (!json.path("idPedido").isIntegralNumber() || comando.getIdPedido() == null || comando.getIdPedido() <= 0) {
            throw protocolo();
        }
        if ("AUTH".equals(comando.getTipo())) {
            Object token = comando.getPayload().get("token");
            if (!(token instanceof String valor) || valor.isBlank() || valor.length() > 4096) {
                throw protocolo();
            }
        }
        if ("ENVIAR_MENSAJE".equals(comando.getTipo())) {
            Object contenido = comando.getPayload().get("contenido");
            Object idMensaje = comando.getPayload().get("clientMessageId");
            if (!(contenido instanceof String valor) || valor.isBlank() || valor.length() > 1000
                    || !(idMensaje instanceof String)) {
                throw protocolo();
            }
            uuid((String) idMensaje);
        }
        return comando;
    }

    private void autenticar(SocketSession sesion, SocketEnvelope comando) {
        String token = (String) comando.getPayload().get("token");
        UsuarioEstadoResponse usuario = auth.validar(token);
        var claims = auth.validarToken(token);
        synchronized (sesiones) {
            if (!sesion.getConexion().isOpen() || !sesiones.containsKey(sesion.getConexion())) {
                return;
            }
            long abiertas = sesiones.values().stream().filter(s -> s.getUsuario() != null
                    && s.getUsuario().getId().equals(usuario.getId())).count();
            if (abiertas >= 3) {
                sesion.getConexion().close(1013, "Limite de conexiones del usuario");
                return;
            }
            sesion.setUsuario(usuario);
            sesion.setExpiresAt(claims.getExpiration().toInstant());
            sesion.setToken(token);
        }
        responder(sesion, comando, "AUTH_OK", Map.of("connectionId", sesion.getConnectionId(),
                "usuario", Map.of("id", usuario.getId(), "nombres", usuario.getNombres() + " " + usuario.getApellidos(),
                        "rol", usuario.getRol()), "expiresAt", sesion.getExpiresAt()));
    }

    private Map<String, Object> consultarAcceso(Long id, SocketSession sesion) {
        var contexto = order.acceso(id, "Bearer " + sesion.getToken());
        if (contexto == null || !(contexto.get("pedidoId") instanceof Number numero) || numero.longValue() != id
                || !(contexto.get("conversacionId") instanceof Number)
                || !(contexto.get("puedeEnviar") instanceof Boolean)) {
            throw new NegocioException(503, "DEPENDENCIA_NO_DISPONIBLE", "La respuesta de Order no es valida.");
        }
        return contexto;
    }

    private void validarMensaje(Map<String, Object> mensaje) {
        if (mensaje == null || !(mensaje.get("id") instanceof Number)
                || !(mensaje.get("secuencia") instanceof Number) || !(mensaje.get("contenido") instanceof String)) {
            throw new NegocioException(503, "DEPENDENCIA_NO_DISPONIBLE", "Consulta el historial del pedido para recuperar el mensaje.");
        }
    }

    public void aceptarEvento(SocketEventRequest evento) {
        synchronized (eventos) {
            eventos.entrySet().removeIf(entry -> entry.getValue().isBefore(Instant.now().minusSeconds(600)));
            if (eventos.containsKey(evento.getEventId())) {
                return;
            }
            if (!listo || !cupoTrabajo.tryAcquire()) {
                throw new NegocioException(503, "DEPENDENCIA_NO_DISPONIBLE", "Socket no puede aceptar eventos ahora.");
            }
            try {
                trabajadores.execute(() -> {
                    try {
                        distribuir(evento);
                    } finally {
                        cupoTrabajo.release();
                    }
                });
            } catch (RejectedExecutionException ex) {
                cupoTrabajo.release();
                throw new NegocioException(503, "DEPENDENCIA_NO_DISPONIBLE", "Socket esta ocupado.");
            }
            eventos.put(evento.getEventId(), Instant.now());
            while (eventos.size() > 2000) {
                eventos.remove(eventos.keySet().iterator().next());
            }
        }
    }

    private void distribuir(SocketEventRequest evento) {
        SocketEnvelope sobre = new SocketEnvelope();
        sobre.setTipo(evento.getTipo());
        sobre.setEventId(evento.getEventId());
        sobre.setGeneradoEn(evento.getGeneradoEn());
        sobre.setIdPedido(evento.getIdPedido());
        sobre.setPayload(evento.getPayload());
        for (SocketSession sesion : sesiones.values()) {
            UsuarioEstadoResponse usuario = sesion.getUsuario();
            if (usuario == null || !sesion.getConexion().isOpen()) {
                continue;
            }
            boolean personal = !"CLIENTE".equals(usuario.getRol());
            boolean propietario = usuario.getId().equals(evento.getClienteId())
                    && sesion.getSuscripciones().contains(evento.getIdPedido())
                    && !"PEDIDO_NUEVO".equals(evento.getTipo());
            if (!personal && !propietario) {
                continue;
            }
            try {
                sesion.setUsuario(auth.validar(sesion.getToken()));
                if (!personal) {
                    consultarAcceso(evento.getIdPedido(), sesion);
                }
                enviar(sesion, sobre);
            } catch (NegocioException ex) {
                error(sesion, null, ex.getStatus(), ex.getCode(), ex.getMessage());
                if (ex.getStatus() == 401) {
                    sesion.getConexion().close(1008, "Sesion invalida");
                }
            } catch (FeignException ex) {
                errorRemoto(sesion, null, ex);
            } catch (RuntimeException ex) {
                log.warn("Evento no enviado: eventId={}, connectionId={}", evento.getEventId(), sesion.getConnectionId());
            }
        }
    }

    private void revisarSesiones() {
        Instant ahora = Instant.now();
        for (SocketSession sesion : sesiones.values()) {
            if (sesion.getExpiresAt() == null ? ahora.isAfter(sesion.getDeadline()) : !ahora.isBefore(sesion.getExpiresAt())) {
                error(sesion, null, 401, "SESION_INVALIDA", "La sesion vencio o no se envio AUTH.");
                sesion.getConexion().close(1008, "Sesion vencida");
            }
        }
    }

    private void responder(SocketSession sesion, SocketEnvelope comando, String tipo, Map<String, Object> payload) {
        SocketEnvelope sobre = new SocketEnvelope();
        sobre.setTipo(tipo);
        sobre.setRequestId(comando == null ? null : comando.getRequestId());
        sobre.setIdPedido(comando == null ? null : comando.getIdPedido());
        sobre.setPayload(payload);
        enviar(sesion, sobre);
    }

    private void error(SocketSession sesion, SocketEnvelope comando, int status, String code, String message) {
        responder(sesion, comando, status == 401 ? "SESION_INVALIDA" : "ERROR",
                Map.of("status", status, "code", code, "message", message));
    }

    private void errorRemoto(SocketSession sesion, SocketEnvelope comando, FeignException ex) {
        int status = Set.of(400, 401, 403, 404, 409).contains(ex.status()) ? ex.status() : 503;
        String code = status == 503 ? "DEPENDENCIA_NO_DISPONIBLE" : "ACCESO_DENEGADO";
        String message = "No se pudo completar la operacion. Consulta el estado del pedido.";
        try {
            var body = mapper.readTree(ex.contentUTF8());
            if (status != 503 && body.path("code").isString() && body.path("message").isString()) {
                code = body.path("code").asString();
                message = body.path("message").asString();
            }
        } catch (RuntimeException ignored) {
            // Una respuesta remota mal formada no expone detalles internos.
        }
        error(sesion, comando, status, code, message);
        if (status == 401 || status == 403) {
            sesion.getConexion().close(1008, "Sesion invalida");
        }
    }

    private void enviar(SocketSession sesion, SocketEnvelope sobre) {
        if (sesion.getConexion().isOpen()) {
            try {
                if (!"SESION_INVALIDA".equals(sobre.getTipo()) && sesion.getExpiresAt() != null
                        && !sesion.getExpiresAt().isAfter(Instant.now())) {
                    sesion.getConexion().close(1008, "Sesion vencida");
                    return;
                }
                if (sesion.getConexion() instanceof WebSocketImpl conexion && conexion.outQueue.size() >= 100) {
                    conexion.close(1013, "Cliente demasiado lento");
                    return;
                }
                sesion.getConexion().send(mapper.writeValueAsString(sobre));
            } catch (RuntimeException ex) {
                sesion.getConexion().close(1013, "Conexion no disponible");
            }
        }
    }

    private SocketEnvelope correlacion(String texto) {
        try {
            var json = mapper.readTree(texto);
            SocketEnvelope comando = new SocketEnvelope();
            comando.setRequestId(uuid(json.path("requestId").asString()));
            if (json.path("idPedido").isIntegralNumber() && json.path("idPedido").asLong() > 0) {
                comando.setIdPedido(json.path("idPedido").asLong());
            }
            return comando;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private UUID uuid(String valor) {
        try {
            UUID id = UUID.fromString(valor);
            if (id.toString().equalsIgnoreCase(valor)) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
            // El protocolo exige UUID completo.
        }
        throw protocolo();
    }

    private NegocioException protocolo() {
        return new NegocioException(400, "PROTOCOLO_INVALIDO", "El comando no cumple el protocolo.");
    }

    private void descartar(SocketSession sesion) {
        synchronized (sesion) {
            sesion.setCerrada(true);
            cupoTrabajo.release(sesion.getPendientes().size());
            sesion.getPendientes().clear();
            sesion.getSuscripciones().clear();
            sesion.setToken(null);
        }
    }

    @Override
    public void onClose(WebSocket conexion, int code, String reason, boolean remote) {
        SocketSession sesion;
        synchronized (sesiones) {
            sesion = sesiones.remove(conexion);
        }
        if (sesion != null) {
            descartar(sesion);
        }
    }

    @Override
    public void onError(WebSocket conexion, Exception ex) {
        if (conexion != null) {
            conexion.close(1011, "Error de conexion");
        } else {
            listo = false;
        }
        log.warn("Error de Socket ({})", ex.getClass().getSimpleName());
    }
}
