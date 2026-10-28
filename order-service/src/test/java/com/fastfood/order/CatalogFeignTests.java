package com.fastfood.order;

import static org.junit.jupiter.api.Assertions.*;

import com.fastfood.order.dto.*;
import com.fastfood.order.exception.*;
import com.fastfood.order.feign.*;
import com.fastfood.order.security.JwtUtil;
import com.sun.net.httpserver.HttpServer;

import io.github.resilience4j.circuitbreaker.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.*;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(
        properties = {
            "eureka.client.enabled=false",
            "spring.datasource.url=jdbc:mysql://localhost:3306/fastfood_order_test_db?createDatabaseIfNotExist=true",
            "spring.jpa.show-sql=false"
        })
class CatalogFeignTests {

    private static final AtomicReference<String> modo = new AtomicReference<>("OK");
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicInteger llamadasCatalogo = new AtomicInteger();
    private static final AtomicInteger estadoAuth = new AtomicInteger(200);
    private static final AtomicInteger llamadasAuth = new AtomicInteger();
    private static final HttpServer servidor = servidor();

    @Autowired CatalogFeignClient catalogo;
    @Autowired AuthEstadoFeignClient auth;
    @Autowired JwtUtil jwt;
    @Autowired CircuitBreakerRegistry circuitos;

    private static HttpServer servidor() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext(
                    "/internal/catalogo/validar",
                    exchange -> {
                        llamadasCatalogo.incrementAndGet();
                        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                        exchange.getRequestBody().readAllBytes();
                        String escenario = modo.get();
                        int status =
                                escenario.equals("FALLA")
                                        ? 503
                                        : escenario.equals("NEGOCIO") ? 409 : 200;
                        String body =
                                status == 200
                                        ? "{\"productos\":[{\"id\":10,\"categoriaId\":20,\"nombre\":\"Hamburguesa\",\"precio\":12.50,\"activo\":true,\"disponible\":true,\"categoriaActiva\":true,\"versionProducto\":0,\"versionCategoria\":0}]}"
                                        : status == 409
                                                ? "{\"code\":\"PRODUCTO_NO_DISPONIBLE\",\"details\":{\"productoIds\":[10]}}"
                                                : "{\"code\":\"DEPENDENCIA_NO_DISPONIBLE\"}";
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(status, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.close();
                    });
            server.createContext(
                    "/internal/auth/usuarios/1/estado",
                    exchange -> {
                        llamadasAuth.incrementAndGet();
                        byte[] bytes =
                                "{\"id\":1,\"nombres\":\"Ana\",\"apellidos\":\"Perez\",\"correo\":\"usuario1@fastfood.test\",\"telefono\":\"999999999\",\"rol\":\"CLIENTE\",\"estado\":\"ACTIVO\",\"versionSeguridad\":1}"
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(estadoAuth.get(), bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.close();
                    });
            server.start();
            return server;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void discoveryLocal(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + servidor.getAddress().getPort();
        registry.add(
                "spring.cloud.discovery.client.simple.instances.catalog-service[0].uri", () -> uri);
        registry.add(
                "spring.cloud.discovery.client.simple.instances.auth-service[0].uri", () -> uri);
    }

    @BeforeEach
    void reiniciarEscenario() {
        modo.set("OK");
        llamadasCatalogo.set(0);
        llamadasAuth.set(0);
        circuitos.circuitBreaker("catalog-service").reset();
    }

    @AfterAll
    static void detenerServidorPropio() {
        servidor.stop(0);
    }

    private CatalogoValidacionResponse consultar() {
        return catalogo.validar(
                new CatalogoValidacionRequest(List.of(10L)),
                "Bearer " + jwt.generarTecnicoCatalogo());
    }

    @Test
    void feignResuelvePorDiscoveryYEnviaTokenTecnicoCorrecto() {
        var respuesta = consultar();
        assertEquals(1, respuesta.getProductos().size());
        assertEquals(
                0,
                new java.math.BigDecimal("12.50")
                        .compareTo(respuesta.getProductos().get(0).getPrecio()));
        var claims =
                Jwts.parser()
                        .verifyWith(
                                Keys.hmacShaKeyFor(
                                        "fastfood_clave_interna_local_academica_2026_distinta_de_jwt_demo"
                                                .getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseSignedClaims(authorization.get().substring(7))
                        .getPayload();
        assertEquals("order-service", claims.getSubject());
        assertEquals(java.util.Set.of("catalog-service"), claims.getAudience());
        assertEquals("CATALOGO_VALIDAR", claims.get("scope"));
        assertEquals(
                1,
                circuitos
                        .circuitBreaker("catalog-service")
                        .getMetrics()
                        .getNumberOfSuccessfulCalls());
    }

    @Test
    void conflictosDeNegocioNoAbrenElCircuitoNiInventanResultados() {
        modo.set("NEGOCIO");
        for (int i = 0; i < 6; i++) {
            RemoteBusinessException rechazo =
                    assertThrows(RemoteBusinessException.class, this::consultar);
            assertEquals(409, rechazo.getStatus());
            assertEquals(List.of(10L), rechazo.getDetails().get("productoIds"));
        }
        assertEquals(
                CircuitBreaker.State.CLOSED,
                circuitos.circuitBreaker("catalog-service").getState());
        assertEquals(
                0,
                circuitos.circuitBreaker("catalog-service").getMetrics().getNumberOfFailedCalls());
        assertEquals(6, llamadasCatalogo.get());
    }

    @Test
    void cincoFallosAbrenCircuitoYAuthPermaneceFueraDeEl() {
        modo.set("FALLA");
        for (int i = 0; i < 5; i++) {
            assertEquals(503, assertThrows(NegocioException.class, this::consultar).getStatus());
        }
        assertEquals(
                CircuitBreaker.State.OPEN, circuitos.circuitBreaker("catalog-service").getState());
        assertEquals(503, assertThrows(NegocioException.class, this::consultar).getStatus());
        assertEquals(
                5,
                llamadasCatalogo.get(),
                "Sin reintentos ni llamada remota cuando el circuito esta abierto");
        assertEquals(1L, auth.obtener(1L, "Bearer " + jwt.generarTecnicoAuth()).getId());
        assertEquals(1, llamadasAuth.get());
        assertFalse(
                circuitos.getAllCircuitBreakers().stream()
                        .anyMatch(c -> c.getName().equals("auth-service")));
        CircuitBreaker circuito = circuitos.circuitBreaker("catalog-service");
        assertEquals(2, circuito.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());
        modo.set("OK");
        circuito.transitionToHalfOpenState();
        consultar();
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuito.getState());
        consultar();
        assertEquals(CircuitBreaker.State.CLOSED, circuito.getState());
    }

    @Test
    void erroresDeAuthNoUsanElDecoderDeCatalogo() {
        estadoAuth.set(404);
        try {
            assertThrows(feign.FeignException.NotFound.class,
                    () -> auth.obtener(1L, "Bearer " + jwt.generarTecnicoAuth()));
            assertFalse(circuitos.getAllCircuitBreakers().stream()
                    .anyMatch(c -> c.getName().equals("auth-service")));
        } finally {
            estadoAuth.set(200);
        }
    }
}
