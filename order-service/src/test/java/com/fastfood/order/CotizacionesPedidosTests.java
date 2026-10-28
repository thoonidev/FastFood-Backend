package com.fastfood.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fastfood.order.dto.*;
import com.fastfood.order.dto.CatalogoValidacionResponse.ProductoSnapshot;
import com.fastfood.order.event.OrderEventPublisher;
import com.fastfood.order.exception.*;
import com.fastfood.order.feign.*;
import com.fastfood.order.model.*;
import com.fastfood.order.model.Pago.EstadoPago;
import com.fastfood.order.repository.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "eureka.client.enabled=false",
            "spring.datasource.url=jdbc:mysql://localhost:3306/fastfood_order_test_db?createDatabaseIfNotExist=true",
            "spring.jpa.show-sql=false"
        })
@AutoConfigureMockMvc
class CotizacionesPedidosTests {

    private static final String SECRET =
            "fastfood_clave_jwt_local_academica_2026_64_caracteres_minimos_demo";

    @Autowired MockMvc mvc;
    @org.springframework.boot.test.web.server.LocalServerPort int puertoHttp;
    @Autowired JsonMapper json;
    @Autowired CotizacionRepository cotizaciones;
    @Autowired PedidoRepository pedidos;
    @Autowired ConversacionRepository conversaciones;
    @Autowired PagoRepository pagos;
    @Autowired PagoIntentoRepository intentos;
    @Autowired SolicitudIdempotenteRepository idempotencias;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.oxm.jaxb.Jaxb2Marshaller marshallerSoap;

    @MockitoBean AuthEstadoFeignClient auth;
    @MockitoBean CatalogFeignClient catalogo;
    @MockitoBean OrderEventPublisher eventos;
    @MockitoBean org.springframework.ws.client.core.WebServiceTemplate comprobantesTemplate;

    private static final java.nio.file.Path reportesTemporales = directorioReportes();

    private static java.nio.file.Path directorioReportes() {
        try { return java.nio.file.Files.createTempDirectory("fastfood-reportes-test-"); }
        catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }

    @org.springframework.test.context.DynamicPropertySource
    static void directorioReportes(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("fastfood.report.directorio", () -> reportesTemporales.toString());
    }

    @AfterAll
    static void eliminarDirectorioVacio() throws Exception {
        java.nio.file.Files.delete(reportesTemporales);
    }

    private final Set<UUID> propias = ConcurrentHashMap.newKeySet();
    private BigDecimal precio = new BigDecimal("12.50");
    private long versionProducto = 0;
    private long versionCategoria = 0;
    private String nombreProducto = "Hamburguesa";
    private String nombreCliente = "Ana";

    @BeforeEach
    void respuestasRemotas() {
        when(auth.obtener(anyLong(), anyString()))
                .thenAnswer(
                        invocacion -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "Auth debe consultarse fuera de la transaccion local");
                            Long id = invocacion.getArgument(0);
                            UsuarioEstadoResponse usuario = new UsuarioEstadoResponse();
                            usuario.setId(id);
                            usuario.setNombres(nombreCliente);
                            usuario.setApellidos("Perez");
                            usuario.setCorreo("usuario" + id + "@fastfood.test");
                            usuario.setTelefono("999999999");
                            usuario.setRol(
                                    id == 2 ? "EMPLEADO" : id == 3 ? "ADMINISTRADOR" : "CLIENTE");
                            usuario.setEstado("ACTIVO");
                            usuario.setVersionSeguridad(1L);
                            return usuario;
                        });
        when(catalogo.validar(any(), anyString()))
                .thenAnswer(
                        invocacion -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "Catalogo debe consultarse fuera de la transaccion local");
                            CatalogoValidacionRequest request = invocacion.getArgument(0);
                            CatalogoValidacionResponse respuesta = new CatalogoValidacionResponse();
                            respuesta.setProductos(
                                    request.getProductoIds().stream()
                                            .map(
                                                    id -> {
                                                        ProductoSnapshot producto =
                                                                new ProductoSnapshot();
                                                        producto.setId(id);
                                                        producto.setCategoriaId(20L);
                                                        producto.setNombre(nombreProducto);
                                                        producto.setPrecio(precio);
                                                        producto.setActivo(true);
                                                        producto.setDisponible(true);
                                                        producto.setCategoriaActiva(true);
                                                        producto.setVersionProducto(
                                                                versionProducto);
                                                        producto.setVersionCategoria(
                                                                versionCategoria);
                                                        return producto;
                                                    })
                                            .toList());
                            return respuesta;
                        });
    }

    @AfterEach
    void limpiarRegistrosPropios() {
        for (UUID id : propias) {
            var pedido = pedidos.findByCotizacionId(id);
            if (pedido.isPresent()) {
                Long pedidoId = pedido.get().getId();
                Long pagoId = jdbc.queryForObject("select id from pagos where pedido_id=?", Long.class, pedidoId);
                jdbc.update("delete from solicitudes_idempotentes where recurso_tipo='PAGO' and recurso_id=?",
                        pagoId.toString());
                jdbc.update("delete from pago_intentos where pago_id=?", pagoId);
                jdbc.update("delete from mensajes_chat where conversacion_id in (select id from conversaciones where pedido_id=?)", pedidoId);
                jdbc.update(
                        "delete from solicitudes_idempotentes where recurso_tipo='PEDIDO' and"
                                + " recurso_id=?",
                        pedidoId.toString());
                for (String tabla :
                        List.of(
                                "pedido_cambios_estado",
                                "pedido_detalles",
                                "pagos",
                                "conversaciones")) {
                    jdbc.update("delete from " + tabla + " where pedido_id=?", pedidoId);
                }
                jdbc.update("delete from pedidos where id=?", pedidoId);
            }
            jdbc.update(
                    "delete from cotizacion_detalles where cotizacion_id=(select id from"
                            + " cotizaciones where id=?)",
                    uuidBytes(id));
            jdbc.update("delete from cotizaciones where id=?", uuidBytes(id));
        }
    }

    private byte[] uuidBytes(UUID id) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private String token(long id) {
        Instant ahora = Instant.now();
        String rol = id == 2 ? "EMPLEADO" : id == 3 ? "ADMINISTRADOR" : "CLIENTE";
        return Jwts.builder()
                .issuer("fastfood-auth")
                .subject(Long.toString(id))
                .claim("usuarioId", id)
                .claim("correo", "usuario" + id + "@fastfood.test")
                .claim("rol", rol)
                .claim("versionSeguridad", 1L)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(3600)))
                .signWith(
                        Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private Map<String, Object> linea(long id, int cantidad, String observacion) {
        return Map.of("productoId", id, "cantidad", cantidad, "observacion", observacion);
    }

    private MockHttpServletResponse cotizar(long actor, List<Map<String, Object>> detalles)
            throws Exception {
        MockHttpServletResponse respuesta =
                mvc.perform(
                                post("/api/cotizaciones")
                                        .header("Authorization", "Bearer " + token(actor))
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of("detalles", detalles))))
                        .andReturn()
                        .getResponse();
        if (respuesta.getStatus() == 201) {
            propias.add(
                    UUID.fromString(
                            json.readTree(respuesta.getContentAsString()).get("id").asString()));
        }
        return respuesta;
    }

    private UUID cotizacion(long actor) throws Exception {
        MockHttpServletResponse respuesta = cotizar(actor, List.of(linea(10, 2, "")));
        assertEquals(201, respuesta.getStatus(), respuesta.getContentAsString());
        return UUID.fromString(json.readTree(respuesta.getContentAsString()).get("id").asString());
    }

    private Map<String, Object> compra(UUID id, String metodo, String monto) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cotizacionId", id.toString());
        request.put("metodoPago", metodo);
        if (monto != null) request.put("montoAnunciado", monto);
        return request;
    }

    private MockHttpServletResponse crear(
            long actor, UUID id, String metodo, String monto, String clave) throws Exception {
        return mvc.perform(
                        post("/api/pedidos")
                                .header("Authorization", "Bearer " + token(actor))
                                .header("Idempotency-Key", clave)
                                .contentType("application/json")
                                .content(json.writeValueAsString(compra(id, metodo, monto))))
                .andReturn()
                .getResponse();
    }

    private long pedido(String metodo) throws Exception {
        UUID id = cotizacion(1);
        MockHttpServletResponse respuesta =
                crear(
                        1,
                        id,
                        metodo,
                        metodo.equals("EFECTIVO") ? "50.00" : null,
                        UUID.randomUUID().toString());
        assertEquals(201, respuesta.getStatus(), respuesta.getContentAsString());
        return json.readTree(respuesta.getContentAsString()).get("id").asLong();
    }

    private MockHttpServletResponse cambiar(long id, String destino, long version, String clave)
            throws Exception {
        return mvc.perform(
                        patch("/api/pedidos/" + id + "/estado")
                                .header("Authorization", "Bearer " + token(2))
                                .header("Idempotency-Key", clave)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "estadoDestino",
                                                        destino,
                                                        "versionEsperada",
                                                        version))))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse cancelar(long id, long version, String motivo, String clave)
            throws Exception {
        return mvc.perform(
                        post("/api/pedidos/" + id + "/cancelacion")
                                .header("Authorization", "Bearer " + token(2))
                                .header("Idempotency-Key", clave)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "motivo",
                                                        motivo,
                                                        "versionEsperada",
                                                        version))))
                .andReturn()
                .getResponse();
    }

    private void vencer(UUID id) {
        new TransactionTemplate(manager)
                .execute(
                        status -> {
                            Cotizacion cotizacion = cotizaciones.findById(id).orElseThrow();
                            cotizacion.setExpiraEn(Instant.now().minusSeconds(1));
                            cotizaciones.saveAndFlush(cotizacion);
                            return null;
                        });
    }

    @Test
    void cotizacionAgrupaObservacionesNormalizadasYCalculaPrecioAutoritativo() throws Exception {
        MockHttpServletResponse respuesta =
                cotizar(
                        1,
                        List.of(
                                linea(10, 1, " sin sal "),
                                linea(10, 2, "sin sal"),
                                linea(10, 1, "sin cebolla")));
        assertEquals(201, respuesta.getStatus(), respuesta.getContentAsString());
        var body = json.readTree(respuesta.getContentAsString());
        assertEquals(2, body.get("detalles").size());
        assertEquals("sin cebolla", body.get("detalles").get(0).get("observacion").asString());
        assertEquals(3, body.get("detalles").get(1).get("cantidad").asInt());
        assertEquals(50.0, body.get("total").asDouble());
        assertEquals(0, body.get("detalles").get(0).get("versionProducto").asLong());
        assertTrue(
                Instant.parse(body.get("expiraEn").asString())
                        .isAfter(Instant.now().plusSeconds(290)));
    }

    @Test
    void cotizacionValidaListaCantidadObservacionYCantidadAgrupada() throws Exception {
        assertEquals(400, cotizar(1, List.of()).getStatus());
        assertEquals(400, cotizar(1, List.of(linea(10, 100, ""))).getStatus());
        assertEquals(
                400,
                cotizar(1, List.of(linea(10, 50, "igual"), linea(10, 50, " igual "))).getStatus());
        assertEquals(400, cotizar(1, List.of(linea(10, 1, "a".repeat(251)))).getStatus());
        assertEquals(
                400,
                cotizar(
                                1,
                                java.util.stream.LongStream.rangeClosed(1, 41)
                                        .mapToObj(id -> linea(id, 1, ""))
                                        .toList())
                        .getStatus());
        mvc.perform(
                        post("/api/cotizaciones")
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(
                                        "{\"detalles\":[{\"productoId\":10,\"cantidad\":1,\"precio\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloClienteCotizaYCreaPedidoYNoSeConfiaEnCabeceras() throws Exception {
        for (long id : List.of(2L, 3L)) {
            assertEquals(403, cotizar(id, List.of(linea(10, 1, ""))).getStatus());
        }
        mvc.perform(post("/api/cotizaciones").header("X-Usuario", "1").header("X-Rol", "CLIENTE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registraPedidoPagoPendienteConversacionEHistorialAtomicamente() throws Exception {
        long id = pedido("EFECTIVO");
        mvc.perform(get("/api/pedidos/" + id).header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPedido").value("REGISTRADO"))
                .andExpect(jsonPath("$.numero").value(String.format("FF-%06d", id)))
                .andExpect(jsonPath("$.tipoEntrega").value("RECOJO_LOCAL"))
                .andExpect(jsonPath("$.total").value(25.0))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.pago.estadoPago").value("PENDIENTE"))
                .andExpect(jsonPath("$.pago.montoAnunciado").value(50.0))
                .andExpect(jsonPath("$.pago.vueltoEstimado").value(25.0))
                .andExpect(jsonPath("$.pago.fechaPago").isEmpty())
                .andExpect(jsonPath("$.cliente.nombres").value("Ana Perez"));
        assertEquals(
                Conversacion.EstadoConversacion.ABIERTA,
                conversaciones.findByPedidoId(id).orElseThrow().getEstado());
        mvc.perform(
                        get("/api/pedidos/" + id + "/cambios-estado")
                                .header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nuevo").value("REGISTRADO"));
        verify(eventos).publicar("PEDIDO_NUEVO", id);
    }

    @Test
    void importesValidanMetodoMontoEscalaYClave() throws Exception {
        UUID id = cotizacion(1);
        for (String monto : List.of("24.99", "50.001", "10000000000")) {
            assertEquals(
                    400, crear(1, id, "EFECTIVO", monto, UUID.randomUUID().toString()).getStatus());
        }
        assertEquals(400, crear(1, id, "EFECTIVO", null, UUID.randomUUID().toString()).getStatus());
        assertEquals(400, crear(1, id, "YAPE", "50.00", UUID.randomUUID().toString()).getStatus());
        assertEquals(400, crear(1, id, "EFECTIVO", "50", "invalido").getStatus());
        mvc.perform(
                        post("/api/pedidos")
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(json.writeValueAsString(compra(id, "EFECTIVO", "50"))))
                .andExpect(status().isBadRequest());
        assertTrue(pedidos.findByCotizacionId(id).isEmpty());
    }

    @Test
    void cotizacionAjenaVencidaOInexistenteNoCreaPedido() throws Exception {
        UUID id = cotizacion(1);
        assertEquals(403, crear(4, id, "EFECTIVO", "50", UUID.randomUUID().toString()).getStatus());
        vencer(id);
        assertEquals(409, crear(1, id, "EFECTIVO", "50", UUID.randomUUID().toString()).getStatus());
        assertTrue(pedidos.findByCotizacionId(id).isEmpty());
        assertEquals(
                404,
                crear(1, UUID.randomUUID(), "YAPE", null, UUID.randomUUID().toString())
                        .getStatus());
    }

    @Test
    void cambioDePrecioVersionOCategoriaImpideRegistrar() throws Exception {
        for (int escenario : List.of(1, 2, 3)) {
            UUID id = cotizacion(1);
            if (escenario == 1) precio = precio.add(BigDecimal.ONE);
            if (escenario == 2) versionProducto++;
            if (escenario == 3) versionCategoria++;
            MockHttpServletResponse respuesta =
                    crear(1, id, "EFECTIVO", "100", UUID.randomUUID().toString());
            assertEquals(409, respuesta.getStatus());
            assertEquals(
                    "CATALOGO_CAMBIO",
                    json.readTree(respuesta.getContentAsString()).get("code").asString());
            assertTrue(pedidos.findByCotizacionId(id).isEmpty());
        }
    }

    @Test
    void productoNoDisponibleYDependenciaCaidaNoGuardanCompra() throws Exception {
        UUID id = cotizacion(1);
        doThrow(
                        new RemoteBusinessException(
                                409, "PRODUCTO_NO_DISPONIBLE", "No disponible", Map.of()))
                .when(catalogo)
                .validar(any(), anyString());
        assertEquals(409, crear(1, id, "EFECTIVO", "50", UUID.randomUUID().toString()).getStatus());
        doThrow(new NegocioException(503, "DEPENDENCIA_NO_DISPONIBLE", "Catalogo caido"))
                .when(catalogo)
                .validar(any(), anyString());
        assertEquals(503, crear(1, id, "EFECTIVO", "50", UUID.randomUUID().toString()).getStatus());
        assertEquals(503, cotizar(1, List.of(linea(10, 1, ""))).getStatus());
        assertTrue(pedidos.findByCotizacionId(id).isEmpty());
    }

    @Test
    void mismaClaveRecuperaRespuestaOriginalInclusoTrasVencerYCambiarPedido() throws Exception {
        UUID cotizacion = cotizacion(1);
        String clave = UUID.randomUUID().toString();
        MockHttpServletResponse primera = crear(1, cotizacion, "EFECTIVO", "50", clave);
        assertEquals(201, primera.getStatus());
        long pedido = json.readTree(primera.getContentAsString()).get("id").asLong();
        assertEquals(
                200, cambiar(pedido, "CONFIRMADO", 0, UUID.randomUUID().toString()).getStatus());
        vencer(cotizacion);
        clearInvocations(catalogo);
        MockHttpServletResponse replay = crear(1, cotizacion, "EFECTIVO", "50.00", clave);
        assertEquals(201, replay.getStatus());
        assertEquals("true", replay.getHeader("Idempotency-Replayed"));
        assertEquals(primera.getContentAsString(), replay.getContentAsString());
        verifyNoInteractions(catalogo);
        assertEquals(409, crear(1, cotizacion, "EFECTIVO", "100", clave).getStatus());
    }

    @Test
    void otraClaveRecuperaLaMismaCompraSinRevalidarCatalogoVencido() throws Exception {
        UUID cotizacion = cotizacion(1);
        MockHttpServletResponse primera =
                crear(1, cotizacion, "YAPE", null, UUID.randomUUID().toString());
        assertEquals(201, primera.getStatus());
        vencer(cotizacion);
        clearInvocations(catalogo);
        MockHttpServletResponse recuperada =
                crear(1, cotizacion, "YAPE", null, UUID.randomUUID().toString());
        assertEquals(200, recuperada.getStatus());
        assertEquals(
                json.readTree(primera.getContentAsString()).get("id").asLong(),
                json.readTree(recuperada.getContentAsString()).get("id").asLong());
        verifyNoInteractions(catalogo);
        assertEquals(
                409,
                crear(1, cotizacion, "TARJETA", null, UUID.randomUUID().toString()).getStatus());
    }

    @Test
    void precioYPerfilHistoricosNoCambianAlEditarOfertaOCliente() throws Exception {
        long id = pedido("TARJETA");
        precio = new BigDecimal("99.00");
        nombreProducto = "Nombre actual";
        nombreCliente = "Nombre nuevo";
        mvc.perform(get("/api/pedidos/" + id).header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25.0))
                .andExpect(jsonPath("$.detalles[0].nombreProducto").value("Hamburguesa"))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(12.5))
                .andExpect(jsonPath("$.cliente.nombres").value("Ana Perez"));
    }

    @Test
    void consultasAislanClienteYFiltranPersonalConPaginacion() throws Exception {
        long id = pedido("EFECTIVO");
        UUID ajena = cotizacion(4);
        assertEquals(201, crear(4, ajena, "YAPE", null, UUID.randomUUID().toString()).getStatus());
        mvc.perform(get("/api/pedidos/mis-pedidos").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/pedidos/" + id).header("Authorization", "Bearer " + token(4)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/pedidos/" + id + "/cambios-estado")
                                .header("Authorization", "Bearer " + token(4)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/pedidos")
                                .header("Authorization", "Bearer " + token(2))
                                .param("metodoPago", "EFECTIVO")
                                .param("estadoPago", "PENDIENTE")
                                .param("clienteId", "1")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(
                        get("/api/pedidos")
                                .header("Authorization", "Bearer " + token(2))
                                .param("desde", "2026-10-08")
                                .param("hasta", "2026-10-07"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/pedidos/mis-pedidos")
                                .header("Authorization", "Bearer " + token(1))
                                .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void busquedaOperativaPorClienteYNumeroRespetaPaginacionYPermisos() throws Exception {
        nombreCliente = "BusquedaOperativa" + UUID.randomUUID().toString().replace("-", "");
        long primero = pedido("EFECTIVO");
        long segundo = pedido("YAPE");
        String numero = String.format(Locale.ROOT, "FF-%06d", primero);
        for (long operador : List.of(2L, 3L)) {
            mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(operador))
                            .param("cliente", "  " + nombreCliente.toUpperCase(Locale.ROOT) + "  ")
                            .param("size", "1").param("page", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].id").value(primero));
            mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(operador))
                            .param("cliente", "USUARIO1@FASTFOOD.TEST")
                            .param("numeroPedido", "  " + numero.toLowerCase(Locale.ROOT) + "  "))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(primero));
        }
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(2))
                        .param("numeroPedido", numero).param("metodoPago", "YAPE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(2))
                        .param("cliente", nombreCliente).param("clienteId", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        for (String literal : List.of("%", "_", "!")) {
            mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(2))
                            .param("cliente", nombreCliente + literal))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        for (String invalido : List.of("33", "FF-000000", "FF-0000001", "FF-9999999999999999999")) {
            mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(2))
                            .param("numeroPedido", invalido)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(2))
                        .param("cliente", "a".repeat(202))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer " + token(1))
                        .param("numeroPedido", numero)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/mis-pedidos").header("Authorization", "Bearer " + token(4))
                        .param("numeroPedido", String.format(Locale.ROOT, "FF-%06d", segundo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void transicionesValidanOrdenPagoVersionYReplay() throws Exception {
        long id = pedido("EFECTIVO");
        assertEquals(409, cambiar(id, "LISTO", 0, UUID.randomUUID().toString()).getStatus());
        String clave = UUID.randomUUID().toString();
        MockHttpServletResponse confirmado = cambiar(id, "CONFIRMADO", 0, clave);
        assertEquals(200, confirmado.getStatus(), confirmado.getContentAsString());
        assertEquals(200, cambiar(id, "CONFIRMADO", 0, clave).getStatus());
        assertEquals(409, cambiar(id, "CONFIRMADO", 1, UUID.randomUUID().toString()).getStatus());
        assertEquals(
                409, cambiar(id, "EN_PREPARACION", 0, UUID.randomUUID().toString()).getStatus());
        assertEquals(
                200, cambiar(id, "EN_PREPARACION", 1, UUID.randomUUID().toString()).getStatus());
        assertEquals(200, cambiar(id, "LISTO", 2, UUID.randomUUID().toString()).getStatus());
        assertEquals(409, cambiar(id, "ENTREGADO", 3, UUID.randomUUID().toString()).getStatus());
        assertEquals(409, cancelar(id, 3, "Tarde", UUID.randomUUID().toString()).getStatus());
        assertEquals(
                409,
                cambiar(pedido("YAPE"), "CONFIRMADO", 0, UUID.randomUUID().toString()).getStatus());
    }

    @Test
    void entregaExigePagoConfirmadoYCierraConversacion() throws Exception {
        long id = pedido("EFECTIVO");
        assertEquals(200, cambiar(id, "CONFIRMADO", 0, UUID.randomUUID().toString()).getStatus());
        assertEquals(
                200, cambiar(id, "EN_PREPARACION", 1, UUID.randomUUID().toString()).getStatus());
        assertEquals(200, cambiar(id, "LISTO", 2, UUID.randomUUID().toString()).getStatus());
        assertEquals(200, pagar(id, "efectivo/confirmacion", 2,
                Map.of("montoRecibido", 50, "versionPagoEsperada", 0), nuevaClave()).getStatus());
        assertEquals(200, cambiar(id, "ENTREGADO", 3, UUID.randomUUID().toString()).getStatus());
        assertEquals(
                Conversacion.EstadoConversacion.CERRADA,
                conversaciones.findByPedidoId(id).orElseThrow().getEstado());
        assertNotNull(conversaciones.findByPedidoId(id).orElseThrow().getFechaCierre());
    }

    @Test
    void cancelacionAnulaPagoCierraChatYRegistraActorSinBorrarDetalles() throws Exception {
        long id = pedido("YAPE");
        String clave = UUID.randomUUID().toString();
        MockHttpServletResponse respuesta = cancelar(id, 0, "  No desea continuar  ", clave);
        assertEquals(200, respuesta.getStatus(), respuesta.getContentAsString());
        var body = json.readTree(respuesta.getContentAsString());
        assertEquals("CANCELADO", body.get("estadoPedido").asString());
        assertEquals("ANULADO", body.get("pago").get("estadoPago").asString());
        assertEquals("No desea continuar", body.get("motivoCancelacion").asString());
        assertEquals(1, body.get("detalles").size());
        assertEquals(
                Conversacion.EstadoConversacion.CERRADA,
                conversaciones.findByPedidoId(id).orElseThrow().getEstado());
        MockHttpServletResponse replay = cancelar(id, 0, "No desea continuar", clave);
        assertEquals("true", replay.getHeader("Idempotency-Replayed"));
        assertEquals(respuesta.getContentAsString(), replay.getContentAsString());
        assertEquals(409, cancelar(id, 1, "Otro", UUID.randomUUID().toString()).getStatus());
        mvc.perform(
                        get("/api/pedidos/" + id + "/cambios-estado")
                                .header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].actorId").value(2));
    }

    @Test
    void falloDelEventoDespuesDelCommitNoConvierteCompraConfirmadaEnError() throws Exception {
        doThrow(new IllegalStateException("Socket no implementado"))
                .when(eventos)
                .publicar(anyString(), anyLong());
        UUID cotizacion = cotizacion(1);
        String clave = UUID.randomUUID().toString();
        MockHttpServletResponse primera = crear(1, cotizacion, "YAPE", null, clave);
        assertEquals(201, primera.getStatus(), primera.getContentAsString());
        assertEquals(201, crear(1, cotizacion, "YAPE", null, clave).getStatus());
        verify(eventos, times(1)).publicar(eq("PEDIDO_NUEVO"), anyLong());
        assertTrue(pedidos.findByCotizacionId(cotizacion).isPresent());
    }

    @Test
    void mismaClaveConcurrenteCreaUnSoloPedidoYPreservaStatus() throws Exception {
        UUID cotizacion = cotizacion(1);
        String clave = UUID.randomUUID().toString();
        List<MockHttpServletResponse> respuestas =
                concurrentes(
                        () -> crear(1, cotizacion, "EFECTIVO", "50", clave),
                        () -> crear(1, cotizacion, "EFECTIVO", "50", clave));
        assertEquals(201, respuestas.get(0).getStatus(), respuestas.get(0).getContentAsString());
        assertEquals(201, respuestas.get(1).getStatus(), respuestas.get(1).getContentAsString());
        assertEquals(
                respuestas.get(0).getContentAsString(), respuestas.get(1).getContentAsString());
        assertEquals(
                1,
                respuestas.stream()
                        .filter(r -> "true".equals(r.getHeader("Idempotency-Replayed")))
                        .count());
        verify(eventos, times(1)).publicar(eq("PEDIDO_NUEVO"), anyLong());
    }

    @Test
    void clavesDistintasSobreMismaCotizacionCreanUnaCompra() throws Exception {
        UUID cotizacion = cotizacion(1);
        List<MockHttpServletResponse> respuestas =
                concurrentes(
                        () -> crear(1, cotizacion, "YAPE", null, UUID.randomUUID().toString()),
                        () -> crear(1, cotizacion, "YAPE", null, UUID.randomUUID().toString()));
        assertEquals(
                Set.of(201, 200),
                Set.of(respuestas.get(0).getStatus(), respuestas.get(1).getStatus()));
        assertEquals(
                json.readTree(respuestas.get(0).getContentAsString()).get("id").asLong(),
                json.readTree(respuestas.get(1).getContentAsString()).get("id").asLong());
    }

    @Test
    void transicionesConcurrentesNoPierdenVersionNiDuplicanHistorial() throws Exception {
        long id = pedido("EFECTIVO");
        List<MockHttpServletResponse> respuestas =
                concurrentes(
                        () -> cambiar(id, "CONFIRMADO", 0, UUID.randomUUID().toString()),
                        () -> cambiar(id, "CONFIRMADO", 0, UUID.randomUUID().toString()));
        assertEquals(
                Set.of(200, 409),
                Set.of(respuestas.get(0).getStatus(), respuestas.get(1).getStatus()));
        assertEquals(1, pedidos.findById(id).orElseThrow().getVersion());
        mvc.perform(
                        get("/api/pedidos/" + id + "/cambios-estado")
                                .header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void respuestaInvalidaDeCatalogoNoInventaraPrecio() throws Exception {
        doReturn(new CatalogoValidacionResponse()).when(catalogo).validar(any(), anyString());
        assertEquals(503, cotizar(1, List.of(linea(10, 1, ""))).getStatus());
    }

    @Test
    void configuracionYConsultasDePagoRespetanAutenticacionPropiedadYPaginacion() throws Exception {
        mvc.perform(get("/api/pagos/configuracion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pagos/configuracion").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.moneda").value("PEN"))
                .andExpect(jsonPath("$.yape.qrUrl").value("/assets/yape-qr.svg"))
                .andExpect(jsonPath("$.efectivo.montosSugeridos[0]").value(50));
        long id = pedido("YAPE");
        for (String ruta : List.of("", "/intentos")) {
            mvc.perform(get("/api/pedidos/" + id + "/pago" + ruta)
                    .header("Authorization", "Bearer " + token(4))).andExpect(status().isForbidden());
            mvc.perform(get("/api/pedidos/" + id + "/pago" + ruta)
                    .header("Authorization", "Bearer " + token(2))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/pedidos/" + id + "/pago/intentos")
                .header("Authorization", "Bearer " + token(1)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pedidos/999999999/pago")
                .header("Authorization", "Bearer " + token(1))).andExpect(status().isNotFound());
    }

    @Test
    void efectivoSoloCobraCuandoEstaListoYCalculaVueltoRealSinEntregar() throws Exception {
        long id = pedido("EFECTIVO");
        Map<String, Object> request = Map.of("montoRecibido", 100, "versionPagoEsperada", 0);
        assertEquals(409, pagar(id, "efectivo/confirmacion", 2, request, nuevaClave()).getStatus());
        preparar(id);
        assertEquals(403, pagar(id, "efectivo/confirmacion", 1, request, nuevaClave()).getStatus());
        assertEquals(400, pagar(id, "efectivo/confirmacion", 2,
                Map.of("montoRecibido", 10, "versionPagoEsperada", 0), nuevaClave()).getStatus());
        String clave = nuevaClave();
        var respuesta = pagar(id, "efectivo/confirmacion", 2, request, clave);
        assertEquals(200, respuesta.getStatus(), respuesta.getContentAsString());
        var pago = json.readTree(respuesta.getContentAsString());
        assertEquals("PAGADO", pago.path("estadoPago").asString());
        assertEquals(50, pago.path("montoAnunciado").asInt());
        assertEquals(25, pago.path("vueltoEstimado").asInt());
        assertEquals(100, pago.path("montoRecibido").asInt());
        assertEquals(75, pago.path("vueltoFinal").asInt());
        assertEquals(1, pago.path("version").asLong());
        assertFalse(pago.path("fechaPago").isNull());
        assertTrue(pago.path("referenciaOperacion").asString().startsWith("EF-"));
        var replay = pagar(id, "efectivo/confirmacion", 2, request, clave);
        assertEquals("true", replay.getHeader("Idempotency-Replayed"));
        assertEquals(respuesta.getContentAsString(), replay.getContentAsString());
        assertEquals(409, pagar(id, "efectivo/confirmacion", 2, request, nuevaClave()).getStatus());
        assertEquals(Pedido.EstadoPedido.LISTO, pedidos.findById(id).orElseThrow().getEstadoPedido());
        assertEquals(200, cambiar(id, "ENTREGADO", 3, nuevaClave()).getStatus());
        assertEquals(1, intentos.countByPagoId(pago.path("id").asLong()));
    }

    @Test
    void yapeConservaRechazoYNuevoIntentoAprobadoHabilitaPreparacion() throws Exception {
        long id = pedido("YAPE");
        var envio = pagar(id, "yape", 1,
                Map.of("referenciaOperacion", "YAPE-DEMO-001", "versionPagoEsperada", 0), nuevaClave());
        assertEquals(200, envio.getStatus(), envio.getContentAsString());
        var body = json.readTree(envio.getContentAsString());
        assertEquals("PENDIENTE_VALIDACION", body.path("estadoPago").asString());
        long primero = body.path("intentoActualId").asLong();
        assertEquals(409, cambiar(id, "CONFIRMADO", 0, nuevaClave()).getStatus());
        assertEquals(409, pagar(id, "yape", 1,
                Map.of("referenciaOperacion", "YAPE-DEMO-002", "versionPagoEsperada", 1), nuevaClave()).getStatus());
        assertEquals(400, pagar(id, "yape/validacion", 2,
                Map.of("intentoId", primero, "aprobar", false, "versionPagoEsperada", 1), nuevaClave()).getStatus());
        var rechazo = pagar(id, "yape/validacion", 2,
                Map.of("intentoId", primero, "aprobar", false, "motivo", "  Referencia incorrecta  ",
                        "versionPagoEsperada", 1), nuevaClave());
        assertEquals(200, rechazo.getStatus(), rechazo.getContentAsString());
        assertEquals("RECHAZADO", json.readTree(rechazo.getContentAsString()).path("estadoPago").asString());
        assertTrue(json.readTree(rechazo.getContentAsString()).path("fechaPago").isNull());
        var segundoEnvio = pagar(id, "yape", 1,
                Map.of("referenciaOperacion", "YAPE-DEMO-002", "versionPagoEsperada", 2), nuevaClave());
        assertEquals(200, segundoEnvio.getStatus());
        long segundo = json.readTree(segundoEnvio.getContentAsString()).path("intentoActualId").asLong();
        assertEquals(409, pagar(id, "yape/validacion", 2,
                Map.of("intentoId", primero, "aprobar", true, "versionPagoEsperada", 3), nuevaClave()).getStatus());
        var aprobacion = pagar(id, "yape/validacion", 3,
                Map.of("intentoId", segundo, "aprobar", true, "versionPagoEsperada", 3), nuevaClave());
        assertEquals(200, aprobacion.getStatus(), aprobacion.getContentAsString());
        assertEquals("PAGADO", json.readTree(aprobacion.getContentAsString()).path("estadoPago").asString());
        assertEquals(200, cambiar(id, "CONFIRMADO", 0, nuevaClave()).getStatus());
        mvc.perform(get("/api/pedidos/" + id + "/pago/intentos")
                .header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].numeroIntento").value(2))
                .andExpect(jsonPath("$.content[0].estado").value("PAGADO"))
                .andExpect(jsonPath("$.content[0].resueltoPorId").value(3))
                .andExpect(jsonPath("$.content[1].motivoRechazo").value("Referencia incorrecta"));
    }

    @Test
    void tarjetaRechazadaPuedeReintentarseYNoGuardaDatosTransitorios() throws Exception {
        long id = pedido("TARJETA");
        var rechazada = pagar(id, "tarjeta", 1, tarjeta("4000000000000002", 0), nuevaClave());
        assertEquals(200, rechazada.getStatus(), rechazada.getContentAsString());
        var rechazo = json.readTree(rechazada.getContentAsString());
        assertEquals("RECHAZADO", rechazo.path("estadoPago").asString());
        assertTrue(rechazo.path("fechaPago").isNull());
        String clave = nuevaClave();
        var request = tarjeta("4111-1111 1111-1111", 1);
        var aprobada = pagar(id, "tarjeta", 1, request, clave);
        assertEquals(200, aprobada.getStatus(), aprobada.getContentAsString());
        var pago = json.readTree(aprobada.getContentAsString());
        assertEquals("PAGADO", pago.path("estadoPago").asString());
        assertFalse(pago.path("fechaPago").isNull());
        request.put("titular", "Otro nombre ficticio");
        request.put("codigoSeguridad", "987");
        var replay = pagar(id, "tarjeta", 1, request, clave);
        assertEquals("true", replay.getHeader("Idempotency-Replayed"));
        assertEquals(aprobada.getContentAsString(), replay.getContentAsString());
        assertEquals(409, pagar(id, "tarjeta", 1, tarjeta("4000000000000002", 1), clave).getStatus());
        assertEquals(409, pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 2), nuevaClave()).getStatus());
        assertEquals(409, cancelar(id, 0, "No desea", nuevaClave()).getStatus());
        List<String> persistidos = jdbc.queryForList(
                "select response_json from solicitudes_idempotentes where recurso_tipo='PAGO' and recurso_id=?",
                String.class, Long.toString(pago.path("id").asLong()));
        for (String guardado : persistidos) {
            assertFalse(guardado.contains("4111"));
            assertFalse(guardado.contains("4000000000000002"));
            assertFalse(guardado.contains("Titular ficticio"));
            assertFalse(guardado.contains("codigoSeguridad"));
            assertFalse(guardado.contains("vencimiento"));
        }
        var historial = mvc.perform(get("/api/pedidos/" + id + "/pago/intentos")
                .header("Authorization", "Bearer " + token(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.content[1].motivoRechazo").value("Pago rechazado"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(historial.contains("4111"));
        TarjetaProcesarRequest dto = json.readValue(json.writeValueAsString(request), TarjetaProcesarRequest.class);
        assertFalse(dto.toString().contains("987"));
        assertFalse(json.writeValueAsString(dto).contains("numeroTarjeta"));
    }

    @Test
    void tarjetaInvalidaNoGeneraIntentosNiRevelaValoresEnErrores() throws Exception {
        long id = pedido("TARJETA");
        var noPermitida = pagar(id, "tarjeta", 1, tarjeta("5555555555554444", 0), nuevaClave());
        assertEquals(400, noPermitida.getStatus());
        assertEquals("TARJETA_PRUEBA_INVALIDA", json.readTree(noPermitida.getContentAsString()).path("code").asString());
        assertFalse(noPermitida.getContentAsString().contains("5555555555554444"));
        int anio = LocalDate.now(ZoneId.of("America/Lima")).getYear();
        for (var invalido : List.of(Map.of("mesVencimiento", 13), Map.of("anioVencimiento", anio - 1),
                Map.of("anioVencimiento", anio + 16), Map.of("codigoSeguridad", "1234"),
                Map.of("titular", "   "), Map.of("numeroTarjeta", "texto"),
                Map.of("versionPagoEsperada", -1), Map.of("aprobar", true))) {
            var request = tarjeta("4111111111111111", 0);
            request.putAll(invalido);
            var respuesta = pagar(id, "tarjeta", 1, request, nuevaClave());
            assertEquals(400, respuesta.getStatus(), respuesta.getContentAsString());
            assertFalse(respuesta.getContentAsString().contains("4111111111111111"));
        }
        long pagoId = jdbc.queryForObject("select id from pagos where pedido_id=?", Long.class, id);
        assertEquals(0, intentos.countByPagoId(pagoId));
        assertEquals(0, jdbc.queryForObject("select count(*) from solicitudes_idempotentes where recurso_tipo='PAGO' and recurso_id=?",
                Integer.class, Long.toString(pagoId)));
    }

    @Test
    void permisosDePagoMetodoVersionYClaveSeVerificanAntesDeCobrar() throws Exception {
        long id = pedido("TARJETA");
        assertEquals(403, pagar(id, "tarjeta", 4, tarjeta("4111111111111111", 0), nuevaClave()).getStatus());
        assertEquals(403, pagar(id, "tarjeta", 2, tarjeta("4111111111111111", 0), nuevaClave()).getStatus());
        assertEquals(403, pagar(id, "tarjeta", 3, tarjeta("4111111111111111", 0), nuevaClave()).getStatus());
        assertEquals(409, pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 1), nuevaClave()).getStatus());
        assertEquals(400, pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), "invalida").getStatus());
        assertEquals(409, pagar(id, "yape", 1,
                Map.of("referenciaOperacion", "YAPE-DEMO-001", "versionPagoEsperada", 0), nuevaClave()).getStatus());
        assertEquals(403, pagar(id, "yape/validacion", 1,
                Map.of("intentoId", 1, "aprobar", true, "versionPagoEsperada", 0), nuevaClave()).getStatus());
    }

    @Test
    void cancelarYapeAnulaSoloElIntentoPendienteYBloqueaValidacion() throws Exception {
        long id = pedido("YAPE");
        var envio = pagar(id, "yape", 1,
                Map.of("referenciaOperacion", "YAPE-DEMO-001", "versionPagoEsperada", 0), nuevaClave());
        long intento = json.readTree(envio.getContentAsString()).path("intentoActualId").asLong();
        assertEquals(200, cancelar(id, 0, "No continuara", nuevaClave()).getStatus());
        var anulado = intentos.findById(intento).orElseThrow();
        assertEquals(PagoIntento.EstadoIntento.ANULADO, anulado.getEstado());
        assertNotNull(anulado.getResueltoEn());
        assertEquals(2L, anulado.getResueltoPorId());
        assertEquals(409, pagar(id, "yape/validacion", 2,
                Map.of("intentoId", intento, "aprobar", true, "versionPagoEsperada", 2), nuevaClave()).getStatus());
    }

    @Test
    void cancelacionConservaElResultadoDeUnIntentoRechazado() throws Exception {
        long id = pedido("TARJETA");
        var rechazo = pagar(id, "tarjeta", 1, tarjeta("4000000000000002", 0), nuevaClave());
        long intento = json.readTree(rechazo.getContentAsString()).path("intentoActualId").asLong();
        assertEquals(200, cancelar(id, 0, "No continuara", nuevaClave()).getStatus());
        assertEquals(PagoIntento.EstadoIntento.RECHAZADO, intentos.findById(intento).orElseThrow().getEstado());
    }

    @Test
    void mismaClaveDeTarjetaConcurrenteConfirmaUnSoloIntento() throws Exception {
        long id = pedido("TARJETA");
        String clave = nuevaClave();
        var respuestas = concurrentes(
                () -> pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), clave),
                () -> pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), clave));
        for (var respuesta : respuestas) {
            assertEquals(200, respuesta.getStatus(), respuesta.getContentAsString());
        }
        assertEquals(respuestas.get(0).getContentAsString(), respuestas.get(1).getContentAsString());
        assertEquals(1, respuestas.stream().filter(r -> "true".equals(r.getHeader("Idempotency-Replayed"))).count());
        long pagoId = json.readTree(respuestas.get(0).getContentAsString()).path("id").asLong();
        assertEquals(1, intentos.countByPagoId(pagoId));
        verify(eventos, times(1)).publicar("PEDIDO_ACTUALIZADO", id);
    }

    @Test
    void dosClavesDeTarjetaConcurrentesNoDuplicanElCobro() throws Exception {
        long id = pedido("TARJETA");
        var respuestas = concurrentes(
                () -> pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), nuevaClave()),
                () -> pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), nuevaClave()));
        assertEquals(Set.of(200, 409), Set.of(respuestas.get(0).getStatus(), respuestas.get(1).getStatus()));
        long pagoId = jdbc.queryForObject("select id from pagos where pedido_id=?", Long.class, id);
        assertEquals(1, intentos.countByPagoId(pagoId));
        assertEquals(EstadoPago.PAGADO, pagos.findById(pagoId).orElseThrow().getEstadoPago());
    }

    @Test
    void validacionYapeConcurrenteConCancelacionSoloConfirmaUnaAccion() throws Exception {
        long id = pedido("YAPE");
        var envio = pagar(id, "yape", 1,
                Map.of("referenciaOperacion", "YAPE-DEMO-001", "versionPagoEsperada", 0), nuevaClave());
        long intento = json.readTree(envio.getContentAsString()).path("intentoActualId").asLong();
        var respuestas = concurrentes(
                () -> pagar(id, "yape/validacion", 2,
                        Map.of("intentoId", intento, "aprobar", true, "versionPagoEsperada", 1), nuevaClave()),
                () -> cancelar(id, 0, "No continuara", nuevaClave()));
        assertEquals(Set.of(200, 409), Set.of(respuestas.get(0).getStatus(), respuestas.get(1).getStatus()));
        var pedido = pedidos.findById(id).orElseThrow();
        var estadoIntento = intentos.findById(intento).orElseThrow().getEstado();
        long pagoId = jdbc.queryForObject("select id from pagos where pedido_id=?", Long.class, id);
        if (pedido.getEstadoPedido() == Pedido.EstadoPedido.CANCELADO) {
            assertEquals(EstadoPago.ANULADO, pagos.findById(pagoId).orElseThrow().getEstadoPago());
            assertEquals(PagoIntento.EstadoIntento.ANULADO, estadoIntento);
        } else {
            assertEquals(Pedido.EstadoPedido.REGISTRADO, pedido.getEstadoPedido());
            assertEquals(EstadoPago.PAGADO, pagos.findById(pagoId).orElseThrow().getEstadoPago());
            assertEquals(PagoIntento.EstadoIntento.PAGADO, estadoIntento);
        }
    }

    @Test
    void falloDeEventoNoReviertePagoNiRepiteCobroAlRecuperarlo() throws Exception {
        long id = pedido("TARJETA");
        doThrow(new IllegalStateException("Evento no disponible")).when(eventos).publicar("PEDIDO_ACTUALIZADO", id);
        String clave = nuevaClave();
        var primera = pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), clave);
        assertEquals(200, primera.getStatus(), primera.getContentAsString());
        var repetida = pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), clave);
        assertEquals(primera.getContentAsString(), repetida.getContentAsString());
        assertEquals("true", repetida.getHeader("Idempotency-Replayed"));
        verify(eventos, times(1)).publicar("PEDIDO_ACTUALIZADO", id);
    }

    @Test
    void chatTieneAutorRealHistorialPorCursorYBandejaPendiente() throws Exception {
        long id = pedido("YAPE");
        var uno = chatear(id, 1, nuevaClave(), "  ¿Ya esta listo?  ");
        assertEquals(200, uno.getStatus(), uno.getContentAsString());
        var mensaje = json.readTree(uno.getContentAsString());
        assertEquals("¿Ya esta listo?", mensaje.path("contenido").asString());
        assertEquals("Ana Perez", mensaje.path("remitenteNombre").asString());
        assertEquals(1, mensaje.path("remitenteId").asLong());
        assertEquals(1, mensaje.path("secuencia").asLong());
        mvc.perform(get("/api/conversaciones").param("pendienteRespuesta", "true")
                .header("Authorization", "Bearer " + token(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertEquals(200, chatear(id, 2, nuevaClave(), "Estamos revisando").getStatus());
        mvc.perform(get("/api/conversaciones").param("pendienteRespuesta", "true")
                .header("Authorization", "Bearer " + token(2)))
                .andExpect(jsonPath("$.totalElements").value(0));
        assertEquals(200, chatear(id, 1, nuevaClave(), "Gracias").getStatus());
        mvc.perform(get("/api/pedidos/" + id + "/mensajes").param("limit", "2")
                .header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.ultimaSecuencia").value(2))
                .andExpect(jsonPath("$.ultimaSecuenciaConversacion").value(3))
                .andExpect(jsonPath("$.hayMas").value(true));
        mvc.perform(get("/api/pedidos/" + id + "/mensajes").param("afterSecuencia", "2")
                .header("Authorization", "Bearer " + token(1)))
                .andExpect(jsonPath("$.content[0].secuencia").value(3))
                .andExpect(jsonPath("$.ultimaSecuencia").value(3)).andExpect(jsonPath("$.hayMas").value(false));
        mvc.perform(get("/api/pedidos/" + id + "/mensajes").param("afterSecuencia", "10")
                .header("Authorization", "Bearer " + token(1)))
                .andExpect(jsonPath("$.ultimaSecuencia").value(10));
    }

    @Test
    void chatRepetidoNoDuplicaNiPublicaYSeRecuperaDespuesDeCerrar() throws Exception {
        long id = pedido("YAPE");
        String clave = nuevaClave();
        var original = chatear(id, 1, clave, "Hola");
        assertEquals(200, original.getStatus());
        assertEquals(original.getContentAsString(), chatear(id, 1, clave, "Hola").getContentAsString());
        assertEquals(409, chatear(id, 1, clave, "Otro texto").getStatus());
        assertEquals(200, cancelar(id, 0, "No continuara", nuevaClave()).getStatus());
        assertEquals(original.getContentAsString(), chatear(id, 1, clave, "Hola").getContentAsString());
        var cerrado = chatear(id, 1, nuevaClave(), "Nuevo mensaje");
        assertEquals(409, cerrado.getStatus());
        assertEquals("CHAT_CERRADO", json.readTree(cerrado.getContentAsString()).path("code").asString());
        verify(eventos, times(1)).publicarMensaje(eq(id), any());
        mvc.perform(get("/api/pedidos/" + id + "/conversacion")
                .header("Authorization", "Bearer " + token(1)))
                .andExpect(jsonPath("$.estadoConversacion").value("CERRADA"))
                .andExpect(jsonPath("$.puedeEnviar").value(false));
        mvc.perform(get("/api/pedidos/" + id + "/mensajes")
                .header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void chatProtegePedidosAjenosYRechazaDatosDeAutorDelNavegador() throws Exception {
        long id = pedido("YAPE");
        assertEquals(403, chatear(id, 4, nuevaClave(), "Hola").getStatus());
        for (String ruta : List.of("/conversacion", "/mensajes")) {
            mvc.perform(get("/api/pedidos/" + id + ruta).header("Authorization", "Bearer " + token(4)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/internal/chat/pedidos/" + id + "/acceso")
                .header("Authorization", "Bearer " + token(4))).andExpect(status().isForbidden());
        mvc.perform(get("/api/conversaciones").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/chat/pedidos/" + id + "/mensajes")
                .header("Authorization", "Bearer " + token(1)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientMessageId", nuevaClave(), "contenido", "Hola", "remitenteId", 2))))
                .andExpect(status().isBadRequest());
        assertEquals(400, chatear(id, 1, nuevaClave(), "   ").getStatus());
        assertEquals(400, chatear(id, 1, nuevaClave(), "x".repeat(1001)).getStatus());
        mvc.perform(post("/api/pedidos/" + id + "/mensajes")
                .header("Authorization", "Bearer " + token(1)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/" + id + "/mensajes").param("afterSecuencia", "-1")
                .header("Authorization", "Bearer " + token(1))).andExpect(status().isBadRequest());
    }

    @Test
    void mensajesConcurrentesTienenSecuenciasUnicasYMismoIdSoloSeGuardaUnaVez() throws Exception {
        long id = pedido("YAPE");
        String clave = nuevaClave();
        var iguales = concurrentes(() -> chatear(id, 1, clave, "Hola"), () -> chatear(id, 1, clave, "Hola"));
        assertEquals(200, iguales.get(0).getStatus());
        assertEquals(iguales.get(0).getContentAsString(), iguales.get(1).getContentAsString());
        var distintos = concurrentes(() -> chatear(id, 1, nuevaClave(), "Cliente"),
                () -> chatear(id, 2, nuevaClave(), "Personal"));
        assertEquals(200, distintos.get(0).getStatus());
        assertEquals(200, distintos.get(1).getStatus());
        assertEquals(Set.of(2L, 3L), Set.of(json.readTree(distintos.get(0).getContentAsString()).path("secuencia").asLong(),
                json.readTree(distintos.get(1).getContentAsString()).path("secuencia").asLong()));
        verify(eventos, times(3)).publicarMensaje(eq(id), any());
    }

    @Test
    void mensajeConcurrenteConCancelacionNoSeGuardaDespuesDelCierre() throws Exception {
        long id = pedido("YAPE");
        String clientMessageId = nuevaClave();
        var resultados = concurrentes(
                () -> chatear(id, 1, clientMessageId, "Todavia estoy consultando"),
                () -> cancelar(id, 0, "No continuara", nuevaClave()));

        var mensaje = resultados.get(0);
        assertEquals(200, resultados.get(1).getStatus());
        assertTrue(mensaje.getStatus() == 200 || mensaje.getStatus() == 409);
        assertEquals(Conversacion.EstadoConversacion.CERRADA,
                conversaciones.findByPedidoId(id).orElseThrow().getEstado());
        long guardados = jdbc.queryForObject(
                "select count(*) from mensajes_chat m join conversaciones c on c.id=m.conversacion_id where c.pedido_id=?",
                Long.class, id);
        assertEquals(mensaje.getStatus() == 200 ? 1L : 0L, guardados);
        assertEquals(409, chatear(id, 1, nuevaClave(), "Mensaje posterior").getStatus());
        if (mensaje.getStatus() == 200) {
            assertEquals(mensaje.getContentAsString(),
                    chatear(id, 1, clientMessageId, "Todavia estoy consultando").getContentAsString());
        }
    }

    @Test
    void socketCaidoNoPierdeMensajePersistido() throws Exception {
        long id = pedido("YAPE");
        doThrow(new IllegalStateException("No disponible")).when(eventos).publicarMensaje(eq(id), any());
        String clave = nuevaClave();
        var mensaje = chatear(id, 1, clave, "Hola");
        assertEquals(200, mensaje.getStatus());
        assertEquals(mensaje.getContentAsString(), chatear(id, 1, clave, "Hola").getContentAsString());
    }

    private MockHttpServletResponse chatear(long id, long actor, String clientMessageId, String contenido) throws Exception {
        return mvc.perform(post("/internal/chat/pedidos/" + id + "/mensajes")
                .header("Authorization", "Bearer " + token(actor)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientMessageId", clientMessageId, "contenido", contenido))))
                .andReturn().getResponse();
    }

    @Test
    void comprobanteRequierePagoYPropietarioAntesDeLlamarASoap() throws Exception {
        long id = pedido("TARJETA");
        assertEquals(409, comprobante(id, 1, true).getStatus());
        assertEquals(403, comprobante(id, 4, true).getStatus());
        verifyNoInteractions(comprobantesTemplate);
    }

    @Test
    void emisionSoapUsaHistoriaFueraDeTransaccionYDevuelveJsonAcademico() throws Exception {
        long id = pedido("TARJETA");
        assertEquals(200, pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), nuevaClave()).getStatus());
        nombreCliente = "Nombre actualizado en Auth";
        nombreProducto = "Producto cambiado en Catalogo";
        var llamadas = new java.util.concurrent.atomic.AtomicInteger();
        when(comprobantesTemplate.marshalSendAndReceive(any(Object.class), any(org.springframework.ws.client.core.WebServiceMessageCallback.class)))
                .thenAnswer(invocacion -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    var request = (com.fastfood.order.soap.contract.GenerarComprobanteRequest) invocacion.getArgument(0);
                    assertEquals("Ana Perez", request.getVenta().getClienteNombres());
                    assertEquals("Hamburguesa", request.getVenta().getDetalle().get(0).getNombreProducto());
                    var documento = new com.fastfood.order.soap.contract.Comprobante();
                    documento.setId(99L); documento.setNumero(String.format(Locale.ROOT, "FFA-%06d", id));
                    documento.setFechaEmision(javax.xml.datatype.DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar("2026-10-07T15:00:00Z"));
                    var xml = new java.io.StringWriter();
                    marshallerSoap.marshal(request, new javax.xml.transform.stream.StreamResult(xml));
                    var copia = (com.fastfood.order.soap.contract.GenerarComprobanteRequest) marshallerSoap.unmarshal(
                            new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml.toString())));
                    assertNotSame(request.getVenta(), copia.getVenta());
                    documento.setVenta(copia.getVenta());
                    var response = new com.fastfood.order.soap.contract.GenerarComprobanteResponse();
                    response.setComprobante(documento); response.setCreado(llamadas.getAndIncrement() == 0); return response;
                });
        var primera = comprobante(id, 1, true);
        assertEquals(201, primera.getStatus(), primera.getContentAsString());
        assertFalse(json.readTree(primera.getContentAsString()).get("validezTributaria").asBoolean());
        assertEquals("Ana Perez", json.readTree(primera.getContentAsString()).get("cliente").get("nombres").asString());
        assertFalse(primera.getContentAsString().contains("Envelope"));
        var repetida = comprobante(id, 2, true);
        assertEquals(200, repetida.getStatus());
        assertEquals(primera.getContentAsString(), repetida.getContentAsString());
        assertEquals(200, comprobante(id, 3, true).getStatus());
        var documentoJson = json.readTree(primera.getContentAsString());
        assertEquals("2026-10-07T15:00:00Z", documentoJson.get("fechaEmision").asString());
        assertEquals("Hamburguesa", documentoJson.get("detalles").get(0).get("nombreProducto").asString());
        assertEquals(25.0, documentoJson.get("total").asDouble());
    }

    @Test
    void falloSoapDevuelve503SinModificarElPago() throws Exception {
        long id = pedido("TARJETA");
        assertEquals(200, pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), nuevaClave()).getStatus());
        when(comprobantesTemplate.marshalSendAndReceive(any(Object.class), any(org.springframework.ws.client.core.WebServiceMessageCallback.class)))
                .thenThrow(new org.springframework.ws.client.WebServiceIOException("detalle privado del transporte"));
        var respuesta = comprobante(id, 1, true);
        assertEquals(503, respuesta.getStatus());
        assertEquals("COMPROBANTE_NO_DISPONIBLE", json.readTree(respuesta.getContentAsString()).get("code").asString());
        assertFalse(respuesta.getContentAsString().contains("detalle privado"));
        assertEquals("PAGADO", jdbc.queryForObject("select estado_pago from pagos where pedido_id=?", String.class, id));
    }

    @Test
    void faultsSoapDeNegocioConservanSuCodigoYEstado() throws Exception {
        long id = pedido("TARJETA");
        assertEquals(200, pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), nuevaClave()).getStatus());
        for (var caso : Map.of("VALIDACION", 400, "COMPROBANTE_NO_ENCONTRADO", 404, "DATOS_COMPROBANTE_CONFLICTO", 409).entrySet()) {
            var factory = new org.springframework.ws.soap.saaj.SaajSoapMessageFactory(); factory.afterPropertiesSet();
            var message = factory.createWebServiceMessage();
            var fault = message.getSoapBody().addClientOrSenderFault("mensaje remoto", Locale.ROOT);
            fault.addFaultDetail().addFaultDetailElement(new javax.xml.namespace.QName("urn:fastfood:comprobantes:v1", "code")).addText(caso.getKey());
            when(comprobantesTemplate.marshalSendAndReceive(any(Object.class), any(org.springframework.ws.client.core.WebServiceMessageCallback.class)))
                    .thenThrow(new org.springframework.ws.soap.client.SoapFaultClientException(message));
            assertEquals(caso.getValue(), comprobante(id, 1, false).getStatus());
        }
    }

    @Test
    void respuestaVaciaYFaultServerDeSoapSeTratanComoNoDisponible() throws Exception {
        long id = pedido("TARJETA");
        assertEquals(200, pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), nuevaClave()).getStatus());
        assertEquals(503, comprobante(id, 1, true).getStatus());
        var factory = new org.springframework.ws.soap.saaj.SaajSoapMessageFactory(); factory.afterPropertiesSet();
        var message = factory.createWebServiceMessage();
        var fault = message.getSoapBody().addServerOrReceiverFault("detalle privado", Locale.ROOT);
        fault.addFaultDetail().addFaultDetailElement(new javax.xml.namespace.QName("urn:fastfood:comprobantes:v1", "code")).addText("VALIDACION");
        when(comprobantesTemplate.marshalSendAndReceive(any(Object.class), any(org.springframework.ws.client.core.WebServiceMessageCallback.class)))
                .thenThrow(new org.springframework.ws.soap.client.SoapFaultClientException(message));
        assertEquals(503, comprobante(id, 1, false).getStatus());
    }

    private MockHttpServletResponse comprobante(long id, long actor, boolean emitir) throws Exception {
        return mvc.perform((emitir ? post("/api/pedidos/" + id + "/comprobante") : get("/api/pedidos/" + id + "/comprobante"))
                .header("Authorization", "Bearer " + token(actor))).andReturn().getResponse();
    }

    @Test
    void reportesVentasCalculanResumenCompletoConLimitesDelDiaEnLima() throws Exception {
        long primero = pedidoPagadoReporte("2001-01-01 05:00:00");
        long segundo = pedidoPagadoReporte("2001-01-02 04:59:59");
        pedidoPagadoReporte("2001-01-02 05:00:00");
        long pendiente = pedido("YAPE");
        jdbc.update("update pedidos set fecha_creacion='2001-01-01 06:00:00' where id=?", pendiente);
        var respuesta = reporte("ventas", "desde=2001-01-01&hasta=2001-01-01&size=1", 3);
        assertEquals(200, respuesta.getStatus(), respuesta.getContentAsString());
        var body = json.readTree(respuesta.getContentAsString());
        assertEquals(2, body.get("totalElements").asInt());
        assertEquals(1, body.get("content").size());
        assertEquals(50, body.get("resumen").get("totalVentas").asInt());
        assertEquals(25, body.get("resumen").get("ticketPromedio").asInt());
        assertEquals(segundo, body.get("content").get(0).get("pedidoId").asLong());
        assertEquals("2001-01-02T04:59:59Z", body.get("content").get(0).get("fechaPago").asString());
        assertEquals(0, json.readTree(reporte("ventas", "desde=2001-01-01&hasta=2001-01-01&metodoPago=YAPE", 3)
                .getContentAsString()).get("totalElements").asInt());
        assertTrue(primero > 0);
    }

    @Test
    void productosAgrupanHistoriaYUsanElNombreDeLaVentaMasReciente() throws Exception {
        long primero = pedidoPagadoReporte("2001-01-01 06:00:00");
        nombreProducto = "Nuevo nombre historico";
        long segundo = pedidoPagadoReporte("2001-01-01 07:00:00");
        jdbc.update("update pedido_detalles set categoria_id=21 where pedido_id=?", segundo);
        var response = reporte("productos", "desde=2001-01-01&hasta=2001-01-01", 3);
        assertEquals(200, response.getStatus(), response.getContentAsString());
        var fila = json.readTree(response.getContentAsString()).get("content").get(0);
        assertEquals("Nuevo nombre historico", fila.get("nombreProducto").asString());
        assertEquals(4, fila.get("unidades").asInt());
        assertEquals(50, fila.get("importeVentas").asInt());
        var historico = json.readTree(reporte("productos", "desde=2001-01-01&hasta=2001-01-01&categoriaId=20", 3)
                .getContentAsString()).get("content").get(0);
        assertEquals("Hamburguesa", historico.get("nombreProducto").asString());
        assertEquals(2, historico.get("unidades").asInt());
        assertTrue(primero > 0);
    }

    @Test
    void reportePedidosIncluyeCanceladosYDashboardMantieneTodosLosEstados() throws Exception {
        long id = pedido("EFECTIVO");
        assertEquals(200, cancelar(id, 0, "Prueba reportes", nuevaClave()).getStatus());
        jdbc.update("update pedidos set fecha_creacion='2001-01-01 06:00:00' where id=?", id);
        var body = json.readTree(reporte("pedidos", "desde=2001-01-01&hasta=2001-01-01&estadoPedido=CANCELADO", 3).getContentAsString());
        assertEquals(1, body.get("resumen").get("totalPedidos").asInt());
        assertEquals(1, body.get("resumen").get("pedidosPorEstado").get("CANCELADO").asInt());
        var dashboard = mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token(3))).andReturn().getResponse();
        assertEquals(200, dashboard.getStatus());
        var indicadores = json.readTree(dashboard.getContentAsString());
        assertEquals(LocalDate.now(ZoneId.of("America/Lima")).toString(), indicadores.get("fechaLocal").asString());
        assertEquals(6, indicadores.get("pedidosPorEstado").size());
        assertTrue(indicadores.get("productoMasVendido").isNull());
        assertEquals(0, indicadores.get("ticketPromedio").asInt());
    }

    @Test
    void reportesExigenAdministradorYValidanFechasYFiltros() throws Exception {
        for (long actor : List.of(1L, 2L)) {
            assertEquals(403, reporte("ventas", "desde=2001-01-01&hasta=2001-01-01", actor).getStatus());
            assertEquals(403, mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token(actor))).andReturn().getResponse().getStatus());
        }
        for (String query : List.of("", "desde=2001-01-02&hasta=2001-01-01", "desde=2001-01-01&hasta=2002-01-02",
                "desde=2001-01-01&hasta=2001-01-01&size=101", "desde=2001-01-01&hasta=2001-01-01&metodoPago=OTRO"))
            assertEquals(400, reporte("ventas", query, 3).getStatus());
        assertEquals(400, reporte("productos", "desde=2001-01-01&hasta=2001-01-01&categoriaId=0", 3).getStatus());
    }

    @Test
    void archivosCsvTxtProtegenTextosYSeLimpianTrasDescarga() throws Exception {
        long id = pedidoPagadoReporte("2001-01-01 06:00:00");
        jdbc.update("update pedidos set cliente_nombres=? where id=?", "=SUM(1,2)\n\"Nombre\"\tApellido", id);
        for (String formato : List.of("CSV", "TXT")) {
            var response = reporte("ventas/exportacion", "desde=2001-01-01&hasta=2001-01-01&formato=" + formato + "&size=1", 3);
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals("no-store", response.getHeader("Cache-Control"));
            assertTrue(response.getHeader("Content-Disposition").contains("ventas_2001-01-01_2001-01-01"));
            String texto = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
            assertTrue(texto.contains("01:00:00-05:00"));
            if (formato.equals("CSV")) {
                assertTrue(texto.startsWith("\uFEFF"));
                assertTrue(texto.contains("'="));
                assertTrue(texto.contains("\"\"Nombre\"\""));
            } else { assertTrue(texto.contains("\\n")); assertTrue(texto.contains("\\t")); }
            try (var files = java.nio.file.Files.list(reportesTemporales)) { assertEquals(0, files.count()); }
        }
    }

    @Test
    void descargaHttpRealEntregaElArchivoCompletoYEliminaElTemporal() throws Exception {
        pedidoPagadoReporte("2001-01-01 06:00:00");
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + puertoHttp
                + "/api/admin/reportes/ventas/exportacion?desde=2001-01-01&hasta=2001-01-01&formato=CSV"))
                .header("Authorization", "Bearer " + token(3)).timeout(Duration.ofSeconds(10)).GET().build();
        var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Disposition").orElseThrow().contains("ventas_2001-01-01_2001-01-01.csv"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        String texto = new String(response.body(), StandardCharsets.UTF_8);
        assertTrue(texto.startsWith("\uFEFF"));
        assertTrue(texto.contains("25.00"));
        try (var archivos = java.nio.file.Files.list(reportesTemporales)) { assertEquals(0, archivos.count()); }
    }

    @Test
    void ventasIncluyenLasTresModalidadesSinSumarIntentosRechazadosPendientesNiCancelados() throws Exception {
        long tarjeta = pedidoPagadoReporte("2001-01-01 06:00:00");
        long efectivo = pedido("EFECTIVO");
        preparar(efectivo);
        assertEquals(200, pagar(efectivo, "efectivo/confirmacion", 2,
                Map.of("montoRecibido", 50, "versionPagoEsperada", 0), nuevaClave()).getStatus());
        long yape = pedido("YAPE");
        var envio = pagar(yape, "yape", 1, Map.of("referenciaOperacion", "YAPE-REPORTE-1", "versionPagoEsperada", 0), nuevaClave());
        long intento = json.readTree(envio.getContentAsString()).get("intentoActualId").asLong();
        assertEquals(200, pagar(yape, "yape/validacion", 2, Map.of("intentoId", intento, "aprobar", false,
                "motivo", "Referencia incorrecta", "versionPagoEsperada", 1), nuevaClave()).getStatus());
        envio = pagar(yape, "yape", 1, Map.of("referenciaOperacion", "YAPE-REPORTE-2", "versionPagoEsperada", 2), nuevaClave());
        intento = json.readTree(envio.getContentAsString()).get("intentoActualId").asLong();
        assertEquals(200, pagar(yape, "yape/validacion", 2, Map.of("intentoId", intento, "aprobar", true,
                "versionPagoEsperada", 3), nuevaClave()).getStatus());
        long cancelado = pedido("EFECTIVO");
        assertEquals(200, cancelar(cancelado, 0, "Prueba reportes", nuevaClave()).getStatus());
        long pendiente = pedido("YAPE");
        assertEquals(200, pagar(pendiente, "yape", 1, Map.of("referenciaOperacion", "YAPE-PENDIENTE", "versionPagoEsperada", 0), nuevaClave()).getStatus());
        long rechazado = pedido("TARJETA");
        assertEquals(200, pagar(rechazado, "tarjeta", 1, tarjeta("4000000000000002", 0), nuevaClave()).getStatus());
        for (long id : List.of(tarjeta, efectivo, yape, cancelado, pendiente, rechazado)) {
            jdbc.update("update pagos set fecha_pago='2001-01-01 06:00:00' where pedido_id=?", id);
            jdbc.update("update pedidos set fecha_creacion='2001-01-01 06:00:00' where id=?", id);
        }
        var body = json.readTree(reporte("ventas", "desde=2001-01-01&hasta=2001-01-01&size=1", 3).getContentAsString());
        assertEquals(3, body.get("totalElements").asInt());
        assertEquals(75, body.get("resumen").get("totalVentas").asInt());
        for (String metodo : List.of("EFECTIVO", "YAPE", "TARJETA"))
            assertEquals(25, body.get("resumen").get("ventasPorMetodo").get(metodo).asInt());
        assertEquals(6, json.readTree(reporte("pedidos", "desde=2001-01-01&hasta=2001-01-01", 3)
                .getContentAsString()).get("totalElements").asInt());
    }

    private long pedidoPagadoReporte(String fechaPago) throws Exception {
        long id = pedido("TARJETA");
        assertEquals(200, pagar(id, "tarjeta", 1, tarjeta("4111111111111111", 0), nuevaClave()).getStatus());
        jdbc.update("update pagos set fecha_pago=? where pedido_id=?", fechaPago, id);
        return id;
    }

    private MockHttpServletResponse reporte(String tipo, String query, long actor) throws Exception {
        return mvc.perform(get("/api/admin/reportes/" + tipo + "?" + query).header("Authorization", "Bearer " + token(actor)))
                .andReturn().getResponse();
    }

    private String nuevaClave() {
        return UUID.randomUUID().toString();
    }

    private void preparar(long id) throws Exception {
        assertEquals(200, cambiar(id, "CONFIRMADO", 0, nuevaClave()).getStatus());
        assertEquals(200, cambiar(id, "EN_PREPARACION", 1, nuevaClave()).getStatus());
        assertEquals(200, cambiar(id, "LISTO", 2, nuevaClave()).getStatus());
    }

    private Map<String, Object> tarjeta(String numero, long version) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("numeroTarjeta", numero);
        request.put("titular", "Titular ficticio");
        request.put("mesVencimiento", 12);
        request.put("anioVencimiento", LocalDate.now(ZoneId.of("America/Lima")).getYear() + 1);
        request.put("codigoSeguridad", "123");
        request.put("versionPagoEsperada", version);
        return request;
    }

    private MockHttpServletResponse pagar(long id, String operacion, long actor, Map<String, Object> request,
            String clave) throws Exception {
        return mvc.perform(post("/api/pedidos/" + id + "/pago/" + operacion)
                .header("Authorization", "Bearer " + token(actor)).header("Idempotency-Key", clave)
                .contentType("application/json").content(json.writeValueAsString(request)))
                .andReturn().getResponse();
    }

    private List<MockHttpServletResponse> concurrentes(
            Callable<MockHttpServletResponse> a, Callable<MockHttpServletResponse> b)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Future<MockHttpServletResponse> primera =
                    executor.submit(
                            () -> {
                                salida.await();
                                return a.call();
                            });
            Future<MockHttpServletResponse> segunda =
                    executor.submit(
                            () -> {
                                salida.await();
                                return b.call();
                            });
            salida.countDown();
            return List.of(primera.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
