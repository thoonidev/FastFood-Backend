package com.fastfood.gateway;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false"})
class GatewayInfrastructureTests {
    private static final String SECRET = "fastfood_clave_jwt_local_academica_2026_64_caracteres_minimos_demo";
    private static final AtomicInteger llamadas = new AtomicInteger();
    private static final AtomicInteger estadoAuth = new AtomicInteger(200);
    private static volatile boolean adminActivo = true;
    private static final HttpServer auth = servidor("auth"), catalog = servidor("catalog"), order = servidor("order");
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    @LocalServerPort int port;

    private static HttpServer servidor(String nombre) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                llamadas.incrementAndGet();
                if ("/api/auth/me".equals(exchange.getRequestURI().getPath())) {
                    String perfil="{\"id\":1,\"rol\":\"ADMINISTRADOR\",\"estado\":\""
                            +(adminActivo ? "ACTIVO" : "INACTIVO")+"\",\"versionSeguridad\":1}";
                    byte[] bytes=perfil.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type","application/json");
                    exchange.sendResponseHeaders(estadoAuth.get(),bytes.length);
                    exchange.getResponseBody().write(bytes);exchange.close();return;
                }
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("negocio")) {
                    byte[] bytes = "{\"status\":503,\"code\":\"COMPROBANTE_NO_DISPONIBLE\",\"message\":\"Puedes volver a intentarlo.\"}"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(503, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.close();
                    return;
                }
                if (query != null && query.startsWith("lento")) {
                    try { Thread.sleep(6500); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
                int estado = query != null && query.startsWith("fallo") ? 503 : 200;
                String texto = estado == 503 ? "detalle privado de infraestructura"
                        : nombre + ":" + exchange.getRequestMethod() + ":" + exchange.getRequestURI()
                            + ":" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.getResponseHeaders().add("Location", "/api/productos/1");
                exchange.sendResponseHeaders(estado, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource
    static void servicios(DynamicPropertyRegistry properties) {
        properties.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri",
                () -> "http://localhost:" + auth.getAddress().getPort());
        properties.add("fastfood.auth.url", () -> "http://localhost:" + auth.getAddress().getPort());
        properties.add("spring.cloud.discovery.client.simple.instances.catalog-service[0].uri",
                () -> "http://localhost:" + catalog.getAddress().getPort());
        properties.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri",
                () -> "http://localhost:" + order.getAddress().getPort());
    }
    @AfterAll static void cerrar() { auth.stop(0); catalog.stop(0); order.stop(0); }
    private HttpRequest.Builder solicitud(String ruta) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).timeout(Duration.ofSeconds(10));
    }
    private HttpResponse<String> enviar(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(String rol) {
        Instant now = Instant.now();
        return Jwts.builder().issuer("fastfood-auth").subject("1").claim("usuarioId", 1L)
                .claim("correo", "demo@fastfood.local").claim("rol", rol).claim("versionSeguridad", 1L)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
    private void error(HttpResponse<String> response, int status, String codigo) {
        assertEquals(status, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        assertEquals(codigo, body.get("code").asText());
        assertEquals(status, body.get("status").asInt());
        assertTrue(body.get("timestamp").asText().endsWith("Z"));
        assertTrue(body.get("fieldErrors").isArray());
        assertTrue(body.get("details").isObject());
        assertFalse(response.body().contains("detalle privado"));
        assertFalse(response.body().contains("stackTrace"));
    }
    @Test void rutasConservanMetodoPrefijoParametrosYCuerpo() throws Exception {
        var registro = enviar(solicitud("/api/auth/register").POST(HttpRequest.BodyPublishers.ofString("{\"nombres\":\"Ana\"}"))
                .header("Content-Type", "application/json"));
        assertEquals(200, registro.statusCode());
        assertEquals("auth:POST:/api/auth/register:{\"nombres\":\"Ana\"}", registro.body());
        assertEquals("catalog:GET:/api/productos?q=pollo:", enviar(solicitud("/api/productos?q=pollo").GET()).body());
        var pedido = enviar(solicitud("/api/pedidos/12").GET().header("Authorization", "Bearer " + token("CLIENTE")));
        assertEquals(200, pedido.statusCode());
        assertEquals("order:GET:/api/pedidos/12:", pedido.body());
    }
    @Test void protegidoExigeBearerSinCookiesNiLoginHtml() throws Exception {
        var response = enviar(solicitud("/api/pedidos").GET());
        error(response, 401, "AUTENTICACION_REQUERIDA");
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        error(enviar(solicitud("/api/pedidos").GET().header("Authorization", "Bearer falso")), 401, "TOKEN_INVALIDO");
        error(enviar(solicitud("/api/pedidos").GET().header("X-Usuario", "1").header("X-Rol", "ADMINISTRADOR")),
                401, "AUTENTICACION_REQUERIDA");
    }
    @Test void rutasInternasSoapYDesconocidasNoSeReenvian() throws Exception {
        int antes = llamadas.get();
        for (String ruta : new String[]{"/internal/auth/usuarios/1/estado", "/ws/comprobantes.wsdl",
                "/api/desconocido"}) {
            var response = enviar(solicitud(ruta).GET().header("Authorization", "Bearer " + token("ADMINISTRADOR")));
            assertTrue(response.statusCode() == 401 || response.statusCode() == 403, response.body());
            assertEquals(response.statusCode() == 401 ? "AUTENTICACION_REQUERIDA" : "ACCESO_DENEGADO",
                    json.readTree(response.body()).get("code").asText());
        }
        assertEquals(antes, llamadas.get());
    }
    @Test void preflightAdmiteOrigenMetodoYCabecerasDelFrontend() throws Exception {
        var response = enviar(solicitud("/api/pedidos").method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type,Idempotency-Key"));
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("http://localhost:5173", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertFalse(response.headers().firstValue("Access-Control-Allow-Credentials").isPresent());
        var real = enviar(solicitud("/api/productos").GET().header("Origin", "http://localhost:5173"));
        assertTrue(real.headers().firstValue("Access-Control-Expose-Headers").orElse("").contains("Content-Disposition"));
    }
    @Test void corsRechazaOrigenCabeceraYMetodoAjeno() throws Exception {
        error(enviar(solicitud("/api/productos").GET().header("Origin", "http://sitio-ajeno.local")), 403, "ACCESO_DENEGADO");
        error(enviar(solicitud("/api/pedidos").method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "TRACE")),
                403, "ACCESO_DENEGADO");
        error(enviar(solicitud("/api/pedidos").method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Rol")), 403, "ACCESO_DENEGADO");
    }
    @Test void falloDeDependenciaEs503SeguroSinReintentar() throws Exception {
        int antes = llamadas.get();
        error(enviar(solicitud("/api/auth/login?fallo").POST(HttpRequest.BodyPublishers.ofString("{}"))),
                503, "DEPENDENCIA_NO_DISPONIBLE");
        assertEquals(antes + 1, llamadas.get());
    }
    @Test void conservaElCodigoJsonDeNegocioSinReenviarLaOperacion() throws Exception {
        int antes = llamadas.get();
        var response = enviar(solicitud("/api/pedidos/1/comprobante?negocio").GET()
                .header("Authorization", "Bearer " + token("CLIENTE")));
        assertEquals(503, response.statusCode());
        assertEquals("COMPROBANTE_NO_DISPONIBLE", json.readTree(response.body()).get("code").asString());
        assertEquals("Puedes volver a intentarlo.", json.readTree(response.body()).get("message").asString());
        assertEquals(antes + 1, llamadas.get());
    }

    @Test void timeoutEs503SinReenviarLaOperacion() throws Exception {
        int antes = llamadas.get();
        error(enviar(solicitud("/api/auth/login?lento").POST(HttpRequest.BodyPublishers.ofString("{}"))),
                503, "DEPENDENCIA_NO_DISPONIBLE");
        assertEquals(antes + 1, llamadas.get());
    }
    @Test void diagnosticoExigeAdministradorVigenteYNoAutorizaSiAuthFalla() throws Exception {
        error(enviar(solicitud("/actuator/health").GET().header("Authorization","Bearer "+token("CLIENTE"))),
                403,"ACCESO_DENEGADO");
        assertEquals(200,enviar(solicitud("/actuator/health").GET()
                .header("Authorization","Bearer "+token("ADMINISTRADOR"))).statusCode());
        try {
            adminActivo=false;
            error(enviar(solicitud("/actuator/health").GET().header("Authorization","Bearer "+token("ADMINISTRADOR"))),
                    401,"SESION_VENCIDA");
            adminActivo=true;estadoAuth.set(503);
            error(enviar(solicitud("/actuator/health").GET().header("Authorization","Bearer "+token("ADMINISTRADOR"))),
                    503,"DEPENDENCIA_NO_DISPONIBLE");
        } finally { adminActivo=true;estadoAuth.set(200); }
    }
}
