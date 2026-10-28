package com.fastfood.socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fastfood.socket.dto.*;
import com.fastfood.socket.feign.*;
import com.fastfood.socket.websocket.FastFoodWebSocketServer;
import feign.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(properties = {"eureka.client.enabled=false", "fastfood.socket.port=0"})
@AutoConfigureMockMvc
class SocketProtocolTests {

    private static final String SECRET = "fastfood_clave_jwt_local_academica_2026_64_caracteres_minimos_demo";
    private static final String INTERNAL = "fastfood_clave_interna_local_academica_2026_distinta_de_jwt_demo";
    @Autowired FastFoodWebSocketServer servidor;
    @Autowired JsonMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean AuthEstadoFeignClient auth;
    @MockitoBean OrderChatFeignClient order;
    private final List<Cliente> clientes = new ArrayList<>();
    private final AtomicBoolean activo = new AtomicBoolean(true);

    @BeforeEach
    void respuestasRemotas() throws Exception {
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!servidor.estaListo() && System.nanoTime() < fin) {
            Thread.sleep(20);
        }
        assertTrue(servidor.estaListo());
        when(auth.obtener(anyLong(), anyString())).thenAnswer(invocacion -> {
            Long id = invocacion.getArgument(0);
            UsuarioEstadoResponse usuario = new UsuarioEstadoResponse();
            usuario.setId(id);
            usuario.setNombres("Usuario");
            usuario.setApellidos("Demo");
            usuario.setCorreo("usuario" + id + "@fastfood.test");
            usuario.setRol(id == 2 ? "EMPLEADO" : id == 3 ? "ADMINISTRADOR" : "CLIENTE");
            usuario.setEstado(activo.get() ? "ACTIVO" : "INACTIVO");
            usuario.setVersionSeguridad(1L);
            return usuario;
        });
        when(order.acceso(anyLong(), anyString())).thenAnswer(invocacion -> Map.of(
                "pedidoId", invocacion.getArgument(0), "conversacionId", 10L,
                "puedeEnviar", true, "ultimaSecuencia", 0L));
        when(order.enviar(anyLong(), anyMap(), anyString())).thenAnswer(invocacion -> {
            Map<String, Object> request = invocacion.getArgument(1);
            return Map.of("id", 35L, "conversacionId", 10L, "secuencia", 1L,
                    "clientMessageId", request.get("clientMessageId"), "contenido", request.get("contenido"));
        });
    }

    @AfterEach
    void cerrarClientesPropios() throws Exception {
        for (Cliente cliente : clientes) {
            cliente.close();
        }
        for (Cliente cliente : clientes) {
            if (!cliente.isClosed()) {
                try {
                    cliente.cierre.get(2, TimeUnit.SECONDS);
                } catch (TimeoutException ex) {
                    cliente.closeConnection(1006, "Fin de la prueba");
                }
            }
        }
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!servidor.getConnections().isEmpty() && System.nanoTime() < fin) {
            Thread.sleep(20);
        }
        assertTrue(servidor.getConnections().isEmpty(), "Conexiones de la prueba sin cerrar");
    }

    @Test
    void authSuscripcionEnvioYPingConservanRequestIdYBearerParticipante() throws Exception {
        Cliente cliente = conectar("http://localhost:5173");
        String token = token(1, 3600);
        autenticar(cliente, token);
        String requestId = comando(cliente, "SUBSCRIBIR_PEDIDO", 12L, Map.of());
        var contexto = recibir(cliente, "SUSCRIPCION_OK");
        assertEquals(requestId, contexto.path("requestId").asString());
        assertEquals(12, contexto.path("idPedido").asLong());
        comando(cliente, "ENVIAR_MENSAJE", 12L, Map.of("clientMessageId", UUID.randomUUID().toString(), "contenido", "Hola"));
        var ack = recibir(cliente, "MENSAJE_ACEPTADO");
        assertEquals(35, ack.path("payload").path("id").asLong());
        verify(order).enviar(eq(12L), anyMap(), eq("Bearer " + token));
        int antes = mockingDetails(auth).getInvocations().size();
        comando(cliente, "PING", null, Map.of());
        recibir(cliente, "PONG");
        assertEquals(antes, mockingDetails(auth).getInvocations().size());
        comando(cliente, "DESUSCRIBIR_PEDIDO", 12L, Map.of());
        recibir(cliente, "ACK");
    }

    @Test
    void origenNoPermitidoYCualquierComandoAntesDeAuthCierranConexion() throws Exception {
        Cliente ajeno = conectar("https://otro.local");
        assertEquals(1008, ajeno.cierre.get(3, TimeUnit.SECONDS));
        Cliente sinAuth = conectar("http://localhost:5173");
        comando(sinAuth, "PING", null, Map.of());
        recibir(sinAuth, "SESION_INVALIDA");
        assertEquals(1008, sinAuth.cierre.get(3, TimeUnit.SECONDS));
    }

    @Test
    void plazoDeAuthYExpiracionDelJwtCierranConexion() throws Exception {
        Cliente sinAuth = conectar("http://localhost:5173");
        assertEquals(1008, sinAuth.cierre.get(7, TimeUnit.SECONDS));
        Cliente cliente = conectar("http://localhost:5173");
        autenticar(cliente, token(1, 2));
        recibir(cliente, "SESION_INVALIDA");
        assertEquals(1008, cliente.cierre.get(4, TimeUnit.SECONDS));
    }

    @Test
    void erroresDeProtocoloMantienenCorrelacionYSoloAceptanCamposDefinidos() throws Exception {
        Cliente cliente = conectar("http://localhost:5173");
        autenticar(cliente, token(1, 3600));
        String id = UUID.randomUUID().toString();
        cliente.send(json.writeValueAsString(Map.of("version", 2, "tipo", "PING", "requestId", id, "payload", Map.of())));
        var error = recibir(cliente, "ERROR");
        assertEquals(id, error.path("requestId").asString());
        assertEquals("PROTOCOLO_INVALIDO", error.path("payload").path("code").asString());
        comando(cliente, "PING", null, Map.of("rol", "ADMINISTRADOR"));
        recibir(cliente, "ERROR");
        comando(cliente, "AUTH", null, Map.of("token", token(1, 3600)));
        recibir(cliente, "ERROR");
        comando(cliente, "PING", null, Map.of());
        recibir(cliente, "PONG");
    }

    @Test
    void frameDemasiadoGrandeYLimiteDeCadenciaCierranSinEncolarSinLimite() throws Exception {
        Cliente grande = conectar("http://localhost:5173");
        grande.send("x".repeat(9000));
        assertEquals(1009, grande.cierre.get(3, TimeUnit.SECONDS));
        Cliente rapido = conectar("http://localhost:5173");
        autenticar(rapido, token(1, 3600));
        for (int i = 0; i < 65 && rapido.isOpen(); i++) {
            try {
                comando(rapido, "PING", null, Map.of());
            } catch (org.java_websocket.exceptions.WebsocketNotConnectedException ignored) {
                break;
            }
        }
        assertEquals(1013, rapido.cierre.get(3, TimeUnit.SECONDS));
    }

    @Test
    void cuartoSocketDelMismoUsuarioEsRechazado() throws Exception {
        for (int i = 0; i < 3; i++) {
            autenticar(conectar("http://localhost:5173"), token(1, 3600));
        }
        Cliente cuarto = conectar("http://localhost:5173");
        comando(cuarto, "AUTH", null, Map.of("token", token(1, 3600)));
        assertEquals(1013, cuarto.cierre.get(3, TimeUnit.SECONDS));
    }

    @Test
    void eventoPrivadoSoloLlegaAPropietarioSuscritoYPersonalYSeDeduplica() throws Exception {
        Cliente propietario = conectar("http://localhost:5173");
        Cliente otro = conectar("http://localhost:5173");
        Cliente personal = conectar("http://localhost:5173");
        autenticar(propietario, token(1, 3600));
        autenticar(otro, token(4, 3600));
        autenticar(personal, token(2, 3600));
        comando(propietario, "SUBSCRIBIR_PEDIDO", 12L, Map.of());
        recibir(propietario, "SUSCRIPCION_OK");
        SocketEventRequest evento = evento("MENSAJE_NUEVO");
        publicar(evento);
        assertEquals(evento.getEventId().toString(), recibir(propietario, "MENSAJE_NUEVO").path("eventId").asString());
        recibir(personal, "MENSAJE_NUEVO");
        assertNull(otro.mensajes.poll(300, TimeUnit.MILLISECONDS));
        publicar(evento);
        assertNull(propietario.mensajes.poll(300, TimeUnit.MILLISECONDS));
        assertNull(personal.mensajes.poll(300, TimeUnit.MILLISECONDS));
        publicar(evento("PEDIDO_NUEVO"));
        recibir(personal, "PEDIDO_NUEVO");
        assertNull(propietario.mensajes.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void cuentaRevocadaNoRecibeEventosYSesionInactivaSeCierra() throws Exception {
        Cliente cliente = conectar("http://localhost:5173");
        autenticar(cliente, token(1, 3600));
        comando(cliente, "SUBSCRIBIR_PEDIDO", 12L, Map.of());
        recibir(cliente, "SUSCRIPCION_OK");
        activo.set(false);
        publicar(evento("PEDIDO_ACTUALIZADO"));
        recibir(cliente, "SESION_INVALIDA");
        assertEquals(1008, cliente.cierre.get(3, TimeUnit.SECONDS));
        assertTrue(cliente.mensajes.stream().noneMatch(frame -> frame.contains("PEDIDO_ACTUALIZADO")));
    }

    @Test
    void authCaidoNoEntregaDatosYPermiteRevalidarSinExtenderElToken() throws Exception {
        Cliente cliente = conectar("http://localhost:5173");
        autenticar(cliente, token(1, 3600));
        doThrow(fallo(503, "{}" )).when(auth).obtener(anyLong(), anyString());
        comando(cliente, "SUBSCRIBIR_PEDIDO", 12L, Map.of());
        var error = recibir(cliente, "ERROR");
        assertEquals(503, error.path("payload").path("status").asInt());
        verify(order, never()).acceso(anyLong(), anyString());
    }

    @Test
    void rechazoDeOrderConservaCodigoYNoAceptaEnvioSinSuscripcion() throws Exception {
        Cliente cliente = conectar("http://localhost:5173");
        autenticar(cliente, token(1, 3600));
        comando(cliente, "SUBSCRIBIR_PEDIDO", 12L, Map.of());
        recibir(cliente, "SUSCRIPCION_OK");
        doThrow(fallo(409, "{\"code\":\"CHAT_CERRADO\",\"message\":\"La conversacion esta cerrada.\"}"))
                .when(order).enviar(anyLong(), anyMap(), anyString());
        comando(cliente, "ENVIAR_MENSAJE", 12L,
                Map.of("clientMessageId", UUID.randomUUID().toString(), "contenido", "Hola"));
        assertEquals("CHAT_CERRADO", recibir(cliente, "ERROR").path("payload").path("code").asString());
        comando(cliente, "ENVIAR_MENSAJE", 13L,
                Map.of("clientMessageId", UUID.randomUUID().toString(), "contenido", "Hola"));
        recibir(cliente, "ERROR");
        assertEquals(1008, cliente.cierre.get(3, TimeUnit.SECONDS));
    }

    @Test
    void endpointInternoSoloAceptaTokenTecnicoCorrectoYEventosDefinidos() throws Exception {
        var request = json.writeValueAsString(evento("PEDIDO_ACTUALIZADO"));
        mvc.perform(post("/internal/socket/eventos").contentType("application/json").content(request))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/socket/eventos").contentType("application/json").content(request)
                .header("Authorization", "Bearer " + token(1, 3600))).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/socket/eventos").contentType("application/json").content(request)
                .header("Authorization", "Bearer " + tecnico("catalog-service"))).andExpect(status().isUnauthorized());
        var incorrecto = evento("TIPO_DESCONOCIDO");
        mvc.perform(post("/internal/socket/eventos").contentType("application/json").content(json.writeValueAsString(incorrecto))
                .header("Authorization", "Bearer " + tecnico("socket-server"))).andExpect(status().isBadRequest());
        mvc.perform(get("/actuator/health").header("Authorization", "Bearer " + token(3, 3600)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private SocketEventRequest evento(String tipo) {
        SocketEventRequest dto = new SocketEventRequest();
        dto.setEventId(UUID.randomUUID());
        dto.setTipo(tipo);
        dto.setGeneradoEn(Instant.now());
        dto.setIdPedido(12L);
        dto.setClienteId(1L);
        dto.setPayload(Map.of("contenido", "Solo para el propietario"));
        return dto;
    }

    @Test
    void limiteTotalDeConexionesRechazaLaNumeroCincuentaYUno() throws Exception {
        for (int i = 0; i < 50; i++) {
            autenticar(conectar("http://localhost:5173"), token(100 + i, 3600));
        }
        Cliente extra = new Cliente(URI.create("ws://localhost:" + servidor.getPort()), "http://localhost:5173");
        clientes.add(extra);
        extra.connectBlocking(3, TimeUnit.SECONDS);
        assertEquals(1013, extra.cierre.get(3, TimeUnit.SECONDS));
    }

    @Test
    void colaSaturadaAplicaBackpressureSinBloquearElListener() throws Exception {
        CountDownLatch liberarAuth = new CountDownLatch(1);
        CountDownLatch authEnEspera = new CountDownLatch(8);
        when(auth.obtener(anyLong(), anyString())).thenAnswer(invocacion -> {
            authEnEspera.countDown();
            liberarAuth.await();
            UsuarioEstadoResponse usuario = new UsuarioEstadoResponse();
            Long id = invocacion.getArgument(0);
            usuario.setId(id);
            usuario.setNombres("Usuario");
            usuario.setApellidos("Demo");
            usuario.setCorreo("usuario" + id + "@fastfood.test");
            usuario.setRol("CLIENTE");
            usuario.setEstado("ACTIVO");
            usuario.setVersionSeguridad(1L);
            return usuario;
        });
        try {
            for (int i = 0; i < 8; i++) {
                Cliente cliente = conectar("http://localhost:5173");
                comando(cliente, "AUTH", null, Map.of("token", token(100 + i, 3600)));
            }
            assertTrue(authEnEspera.await(10, TimeUnit.SECONDS));
            for (Cliente cliente : clientes) {
                for (int j = 0; j < 30 && cliente.isOpen(); j++) {
                    try {
                        comando(cliente, "PING", null, Map.of());
                    } catch (org.java_websocket.exceptions.WebsocketNotConnectedException ignored) {
                        break;
                    }
                }
            }
            long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (clientes.stream().noneMatch(c -> c.cierre.getNow(0) == 1013) && System.nanoTime() < fin) {
                Thread.sleep(20);
            }
            assertTrue(clientes.stream().anyMatch(c -> c.cierre.getNow(0) == 1013));
        } finally {
            liberarAuth.countDown();
        }
    }

    private void publicar(SocketEventRequest evento) throws Exception {
        mvc.perform(post("/internal/socket/eventos").contentType("application/json")
                .content(json.writeValueAsString(evento)).header("Authorization", "Bearer " + tecnico("socket-server")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.aceptado").value(true));
    }

    private String tecnico(String destino) {
        Instant ahora = Instant.now();
        return Jwts.builder().issuer("fastfood-internal").subject("order-service").audience().add(destino).and()
                .claim("scope", "EVENTO_PUBLICAR").id(UUID.randomUUID().toString()).issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(INTERNAL.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    private String token(long id, long segundos) {
        Instant ahora = Instant.now();
        return Jwts.builder().issuer("fastfood-auth").subject(Long.toString(id)).claim("usuarioId", id)
                .claim("correo", "usuario" + id + "@fastfood.test").claim("versionSeguridad", 1L)
                .claim("rol", id == 2 ? "EMPLEADO" : id == 3 ? "ADMINISTRADOR" : "CLIENTE")
                .issuedAt(Date.from(ahora)).expiration(Date.from(ahora.plusSeconds(segundos)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    private FeignException fallo(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "http://localhost/internal", Map.of(),
                (byte[]) null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("Order", Response.builder().status(status).reason("Error")
                .request(request).headers(Map.of()).body(body, StandardCharsets.UTF_8).build());
    }

    private Cliente conectar(String origin) throws Exception {
        Cliente cliente = new Cliente(URI.create("ws://localhost:" + servidor.getPort()), origin);
        clientes.add(cliente);
        assertTrue(cliente.connectBlocking(5, TimeUnit.SECONDS),
                () -> "Conexion fallida: cierre=" + cliente.cierre.getNow(0)
                        + ", error=" + cliente.error);
        return cliente;
    }

    private void autenticar(Cliente cliente, String token) throws Exception {
        comando(cliente, "AUTH", null, Map.of("token", token));
        assertNotNull(recibir(cliente, "AUTH_OK").path("payload").path("connectionId"));
    }

    private String comando(Cliente cliente, String tipo, Long idPedido, Map<String, Object> payload) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> sobre = new LinkedHashMap<>(Map.of("version", 1, "tipo", tipo, "requestId", id, "payload", payload));
        if (idPedido != null) {
            sobre.put("idPedido", idPedido);
        }
        cliente.send(json.writeValueAsString(sobre));
        return id;
    }

    private JsonNode recibir(Cliente cliente, String tipo) throws Exception {
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String ultimoFrame = null;
        while (System.nanoTime() < fin) {
            String frame = cliente.mensajes.poll(100, TimeUnit.MILLISECONDS);
            if (frame != null) {
                ultimoFrame = frame;
                JsonNode respuesta = json.readTree(frame);
                if (tipo.equals(respuesta.path("tipo").asString())) {
                    return respuesta;
                }
            }
        }
        throw new AssertionError("No llego " + tipo + "; ultimo frame=" + ultimoFrame);
    }

    private static class Cliente extends WebSocketClient {
        final BlockingQueue<String> mensajes = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> cierre = new CompletableFuture<>();
        volatile Exception error;

        Cliente(URI uri, String origin) {
            super(uri, Map.of("Origin", origin));
        }

        @Override public void onOpen(ServerHandshake handshake) { }
        @Override public void onMessage(String texto) { mensajes.add(texto); }
        @Override public void onClose(int code, String reason, boolean remote) { cierre.complete(code); }
        @Override public void onError(Exception ex) { error = ex; }
    }
}
