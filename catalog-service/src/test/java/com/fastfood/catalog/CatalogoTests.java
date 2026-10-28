package com.fastfood.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fastfood.catalog.dto.UsuarioEstadoResponse;
import com.fastfood.catalog.feign.AuthEstadoFeignClient;
import com.fastfood.catalog.repository.CategoriaRepository;
import com.fastfood.catalog.repository.ProductoRepository;

import feign.FeignException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(
        properties = {
            "eureka.client.enabled=false",
            "spring.datasource.url=jdbc:mysql://localhost:3306/fastfood_catalog_test_db?createDatabaseIfNotExist=true"
        })
@AutoConfigureMockMvc
class CatalogoTests {

    private static final String SECRET =
            "fastfood_clave_jwt_local_academica_2026_64_caracteres_minimos_demo";
    private static final String INTERNAL =
            "fastfood_clave_interna_local_academica_2026_distinta_de_jwt_demo";

    @Autowired MockMvc mvc;

    @Autowired JsonMapper json;

    @Autowired ProductoRepository productos;

    @Autowired CategoriaRepository categorias;

    @MockitoBean AuthEstadoFeignClient auth;

    private final List<Long> categoriaIds = new CopyOnWriteArrayList<>();
    private final List<Long> productoIds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void usuarioVigente() {
        when(auth.obtener(anyLong(), anyString()))
                .thenAnswer(
                        invocacion -> {
                            Long id = invocacion.getArgument(0);
                            UsuarioEstadoResponse usuario = new UsuarioEstadoResponse();
                            usuario.setId(id);
                            usuario.setCorreo("usuario" + id + "@fastfood.test");
                            usuario.setRol(
                                    id == 1 ? "ADMINISTRADOR" : id == 2 ? "CLIENTE" : "EMPLEADO");
                            usuario.setEstado("ACTIVO");
                            usuario.setVersionSeguridad(1L);
                            return usuario;
                        });
    }

    @AfterEach
    void limpiarRegistrosPropios() {
        productos.deleteAllById(productoIds);
        categorias.deleteAllById(categoriaIds);
    }

    private String token(long id) {
        String rol = id == 1 ? "ADMINISTRADOR" : id == 2 ? "CLIENTE" : "EMPLEADO";
        Instant ahora = Instant.now();
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

    private String tecnico(String sujeto, String destino, String scope, int segundos) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .issuer("fastfood-internal")
                .subject(sujeto)
                .audience()
                .add(destino)
                .and()
                .claim("scope", scope)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(segundos)))
                .signWith(
                        Keys.hmacShaKeyFor(INTERNAL.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }

    private String tecnico() {
        return tecnico("order-service", "catalog-service", "CATALOGO_VALIDAR", 60);
    }

    private MockHttpServletResponse crearCategoria(String nombre) throws Exception {
        MockHttpServletResponse respuesta =
                mvc.perform(
                                post("/api/admin/categorias")
                                        .header("Authorization", "Bearer " + token(1))
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "nombre",
                                                                nombre,
                                                                "descripcion",
                                                                "Descripcion"))))
                        .andReturn()
                        .getResponse();
        if (respuesta.getStatus() == 201) {
            categoriaIds.add(json.readTree(respuesta.getContentAsString()).get("id").asLong());
        }
        return respuesta;
    }

    private long categoria() throws Exception {
        MockHttpServletResponse respuesta = crearCategoria("Prueba " + UUID.randomUUID());
        assertEquals(201, respuesta.getStatus(), respuesta.getContentAsString());
        return json.readTree(respuesta.getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> datosProducto(long categoriaId, String nombre) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("categoriaId", categoriaId);
        datos.put("nombre", nombre);
        datos.put("descripcion", "Descripcion del producto");
        datos.put("precio", "12.50");
        datos.put("imagenUrl", "https://example.test/producto.png");
        return datos;
    }

    private MockHttpServletResponse altaProducto(Map<String, Object> datos) throws Exception {
        MockHttpServletResponse respuesta =
                mvc.perform(
                                post("/api/admin/productos")
                                        .header("Authorization", "Bearer " + token(1))
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(datos)))
                        .andReturn()
                        .getResponse();
        if (respuesta.getStatus() == 201) {
            productoIds.add(json.readTree(respuesta.getContentAsString()).get("id").asLong());
        }
        return respuesta;
    }

    private long producto(long categoriaId, String nombre) throws Exception {
        MockHttpServletResponse respuesta = altaProducto(datosProducto(categoriaId, nombre));
        assertEquals(201, respuesta.getStatus(), respuesta.getContentAsString());
        return json.readTree(respuesta.getContentAsString()).get("id").asLong();
    }

    private MockHttpServletResponse estadoProducto(
            long id, boolean activo, boolean disponible, long version) throws Exception {
        return mvc.perform(
                        patch("/api/admin/productos/" + id + "/estado")
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "activo",
                                                        activo,
                                                        "disponible",
                                                        disponible,
                                                        "versionEsperada",
                                                        version))))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse estadoCategoria(long id, boolean activo, long version)
            throws Exception {
        return mvc.perform(
                        patch("/api/admin/categorias/" + id + "/estado")
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "activo",
                                                        activo,
                                                        "versionEsperada",
                                                        version))))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse validar(List<Long> ids, String token) throws Exception {
        return mvc.perform(
                        post("/internal/catalogo/validar")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(json.writeValueAsString(Map.of("productoIds", ids))))
                .andReturn()
                .getResponse();
    }

    @Test
    void consultasPublicasNoNecesitanAuthNiAceptanIdentidadDelNavegador() throws Exception {
        long categoria = categoria();
        long producto = producto(categoria, "Publico");
        clearInvocations(auth);
        mvc.perform(get("/api/productos/" + producto).header("Authorization", "Bearer invalido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(12.50))
                .andExpect(jsonPath("$.categoria.id").value(categoria))
                .andExpect(jsonPath("$.activo").doesNotExist())
                .andExpect(jsonPath("$.versionCategoria").doesNotExist());
        mvc.perform(get("/api/categorias")).andExpect(status().isOk());
        verifyNoInteractions(auth);
        mvc.perform(post("/api/admin/productos").header("X-Rol", "ADMINISTRADOR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soloAdministradorGestionaCatalogoInclusoEnAccesoDirecto() throws Exception {
        for (long usuario : List.of(2L, 3L)) {
            mvc.perform(
                            get("/api/admin/productos")
                                    .header("Authorization", "Bearer " + token(usuario)))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/api/admin/categorias")
                                    .header("Authorization", "Bearer " + token(usuario)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/actuator/health").header("Authorization", "Bearer " + token(usuario)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/productos").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/health").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/productos/1").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authConfirmaEstadoVersionYRolAntesDeUnaEscritura() throws Exception {
        UsuarioEstadoResponse usuario = new UsuarioEstadoResponse();
        usuario.setId(1L);
        usuario.setCorreo("usuario1@fastfood.test");
        usuario.setRol("ADMINISTRADOR");
        usuario.setEstado("INACTIVO");
        usuario.setVersionSeguridad(1L);
        when(auth.obtener(anyLong(), anyString())).thenReturn(usuario);
        mvc.perform(get("/api/admin/productos").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USUARIO_INACTIVO"));
        usuario.setEstado("ACTIVO");
        usuario.setVersionSeguridad(2L);
        mvc.perform(get("/api/admin/productos").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESION_VENCIDA"));
        usuario.setVersionSeguridad(1L);
        usuario.setRol("CLIENTE");
        mvc.perform(get("/api/admin/productos").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authCaidoORespuestaIncompletaProduce503YNoAutoriza() throws Exception {
        FeignException fallo =
                FeignException.errorStatus(
                        "AuthEstadoFeignClient#obtener",
                        feign.Response.builder()
                                .status(503)
                                .reason("No disponible")
                                .request(
                                        feign.Request.create(
                                                feign.Request.HttpMethod.GET,
                                                "http://localhost/internal/auth/usuarios/1/estado",
                                                Map.of(),
                                                null,
                                                StandardCharsets.UTF_8,
                                                null))
                                .headers(Map.of())
                                .build());
        when(auth.obtener(anyLong(), anyString())).thenThrow(fallo);
        mvc.perform(get("/api/admin/productos").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isServiceUnavailable());
        doReturn(new UsuarioEstadoResponse()).when(auth).obtener(anyLong(), anyString());
        mvc.perform(get("/api/admin/productos").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void categoriaNormalizaNombreYEsUnicaSinDistinguirMayusculas() throws Exception {
        String nombre = "Prueba " + UUID.randomUUID();
        MockHttpServletResponse respuesta = crearCategoria("  " + nombre + "  ");
        assertEquals(201, respuesta.getStatus());
        var body = json.readTree(respuesta.getContentAsString());
        assertEquals(nombre, body.get("nombre").asText());
        assertTrue(body.get("activo").asBoolean());
        assertEquals(0, body.get("version").asLong());
        assertTrue(body.get("fechaCreacion").asText().endsWith("Z"));
        assertEquals(409, crearCategoria(nombre.toUpperCase(Locale.ROOT)).getStatus());
    }

    @Test
    void productoIniciaActivoDisponibleYNoDestacado() throws Exception {
        long categoria = categoria();
        long producto = producto(categoria, "  Hamburguesa  ");
        mvc.perform(
                        get("/api/admin/productos/" + producto)
                                .header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Hamburguesa"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.destacado").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.versionCategoria").value(0));
    }

    @Test
    void validaPrecioImagenCategoriaYCamposExtra() throws Exception {
        long categoria = categoria();
        for (Object precio : List.of("0", "-1", "12.501", "10000000000")) {
            Map<String, Object> datos = datosProducto(categoria, "Invalido");
            datos.put("precio", precio);
            assertEquals(400, altaProducto(datos).getStatus());
        }
        for (String imagen :
                List.of("javascript:alert(1)", "file:///imagen", "https:///sin-host", "http://")) {
            Map<String, Object> datos = datosProducto(categoria, "Invalido");
            datos.put("imagenUrl", imagen);
            assertEquals(400, altaProducto(datos).getStatus());
        }
        Map<String, Object> datos = datosProducto(categoria, "Invalido");
        datos.put("activo", false);
        assertEquals(400, altaProducto(datos).getStatus());
        datos.remove("activo");
        datos.put("categoriaId", Long.MAX_VALUE);
        assertEquals(404, altaProducto(datos).getStatus());
        mvc.perform(
                        post("/api/admin/productos")
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JSON_INVALIDO"));
    }

    @Test
    void buscaFiltraDestacadosCategoriaYPaginaAntesDeDevolverResultados() throws Exception {
        long categoria = categoria();
        long otraCategoria = categoria();
        String clave = "Buscar " + UUID.randomUUID();
        long primero = producto(categoria, clave + " A");
        producto(categoria, clave + " B");
        producto(otraCategoria, clave + " C");
        Map<String, Object> edicion = datosProducto(categoria, clave + " A");
        edicion.put("destacado", true);
        edicion.put("versionEsperada", 0);
        mvc.perform(
                        put("/api/admin/productos/" + primero)
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(json.writeValueAsString(edicion)))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/productos")
                                .param("q", clave)
                                .param("categoriaId", "" + categoria)
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(primero));
        mvc.perform(get("/api/productos").param("q", clave).param("destacado", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/productos").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/productos").param("categoriaId", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/categorias").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void noDisponibleEsVisiblePeroNoComprableEInactivoNoEsPublico() throws Exception {
        long producto = producto(categoria(), "No disponible");
        assertEquals(200, estadoProducto(producto, true, false, 0).getStatus());
        mvc.perform(get("/api/productos/" + producto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));
        MockHttpServletResponse rechazo = validar(List.of(producto), tecnico());
        assertEquals(409, rechazo.getStatus());
        assertEquals(
                producto,
                json.readTree(rechazo.getContentAsString())
                        .get("details")
                        .get("productoIds")
                        .get(0)
                        .asLong());
        assertEquals(200, estadoProducto(producto, false, true, 1).getStatus());
        mvc.perform(get("/api/productos/" + producto)).andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/admin/productos/" + producto)
                                .header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        assertTrue(productos.existsById(producto));
    }

    @Test
    void desactivarCategoriaOcultaProductosYSuReactivacionNoCambiaSusFlags() throws Exception {
        long categoria = categoria();
        long producto = producto(categoria, "Categoria oculta");
        assertEquals(200, estadoCategoria(categoria, false, 0).getStatus());
        mvc.perform(get("/api/productos/" + producto)).andExpect(status().isNotFound());
        mvc.perform(get("/api/productos").param("categoriaId", "" + categoria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertEquals(409, validar(List.of(producto), tecnico()).getStatus());
        assertTrue(productos.findById(producto).orElseThrow().isActivo());
        assertEquals(200, estadoCategoria(categoria, true, 1).getStatus());
        mvc.perform(get("/api/productos/" + producto)).andExpect(status().isOk());
    }

    @Test
    void editarProductoYFlagsRechazaVersionObsoleta() throws Exception {
        long categoria = categoria();
        long producto = producto(categoria, "Original");
        Map<String, Object> datos = datosProducto(categoria, "Editado");
        datos.put("precio", "18.00");
        datos.put("destacado", true);
        datos.put("versionEsperada", 0);
        mvc.perform(
                        put("/api/admin/productos/" + producto)
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(json.writeValueAsString(datos)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertEquals(409, estadoProducto(producto, false, true, 0).getStatus());
        mvc.perform(
                        put("/api/admin/productos/" + producto)
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(json.writeValueAsString(datos)))
                .andExpect(status().isConflict());
        assertEquals("Editado", productos.findById(producto).orElseThrow().getNombre());
        assertEquals(200, estadoProducto(producto, true, true, 1).getStatus());
        assertEquals(2, productos.findById(producto).orElseThrow().getVersion());
    }

    @Test
    void editarCategoriaExigeVersionYRechazaNombreDuplicado() throws Exception {
        long categoria = categoria();
        String nombre = "Editar " + UUID.randomUUID();
        mvc.perform(
                        put("/api/admin/categorias/" + categoria)
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "nombre",
                                                        nombre,
                                                        "descripcion",
                                                        "Editada",
                                                        "versionEsperada",
                                                        0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertEquals(409, estadoCategoria(categoria, false, 0).getStatus());
        assertEquals(409, crearCategoria(nombre).getStatus());
        mvc.perform(
                        put("/api/admin/categorias/" + categoria)
                                .header("Authorization", "Bearer " + token(1))
                                .contentType("application/json")
                                .content(json.writeValueAsString(Map.of("nombre", nombre))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validacionInternaDevuelveSnapshotsEnOrdenYSinListasParciales() throws Exception {
        long categoria = categoria();
        long a = producto(categoria, "A");
        long b = producto(categoria, "B");
        clearInvocations(auth);
        MockHttpServletResponse respuesta = validar(List.of(b, a), tecnico());
        assertEquals(200, respuesta.getStatus(), respuesta.getContentAsString());
        var snapshots = json.readTree(respuesta.getContentAsString()).get("productos");
        assertEquals(b, snapshots.get(0).get("id").asLong());
        assertEquals(
                0,
                new java.math.BigDecimal("12.50")
                        .compareTo(
                                new java.math.BigDecimal(
                                        snapshots.get(0).get("precio").asString())));
        assertEquals(0, snapshots.get(0).get("versionProducto").asLong());
        assertEquals(0, snapshots.get(0).get("versionCategoria").asLong());
        verifyNoInteractions(auth);
        respuesta = validar(List.of(a, Long.MAX_VALUE), tecnico());
        assertEquals(409, respuesta.getStatus());
        assertFalse(json.readTree(respuesta.getContentAsString()).has("productos"));
        assertEquals(400, validar(List.of(a, a), tecnico()).getStatus());
        assertEquals(400, validar(List.of(), tecnico()).getStatus());
        assertEquals(
                400,
                validar(java.util.stream.LongStream.rangeClosed(1, 41).boxed().toList(), tecnico())
                        .getStatus());
    }

    @Test
    void validacionInternaSoloAceptaOrderDestinoScopeYDuracionCorrectos() throws Exception {
        long producto = producto(categoria(), "Tecnico");
        for (String token :
                List.of(
                        token(1),
                        tecnico("catalog-service", "catalog-service", "CATALOGO_VALIDAR", 60),
                        tecnico("order-service", "auth-service", "CATALOGO_VALIDAR", 60),
                        tecnico("order-service", "catalog-service", "OTRO", 60),
                        tecnico("order-service", "catalog-service", "CATALOGO_VALIDAR", 61))) {
            assertEquals(401, validar(List.of(producto), token).getStatus());
        }
        mvc.perform(get("/api/admin/productos").header("Authorization", "Bearer " + tecnico()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/internal/catalogo/validar")
                                .header("Authorization", "Bearer " + tecnico()))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultasAdminIncluyenInactivosYFiltranFlags() throws Exception {
        long categoria = categoria();
        long producto = producto(categoria, "Admin");
        assertEquals(200, estadoProducto(producto, false, false, 0).getStatus());
        mvc.perform(
                        get("/api/admin/productos")
                                .header("Authorization", "Bearer " + token(1))
                                .param("categoriaId", "" + categoria)
                                .param("activo", "false")
                                .param("disponible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        assertEquals(200, estadoCategoria(categoria, false, 0).getStatus());
        String nombre = categorias.findById(categoria).orElseThrow().getNombre();
        mvc.perform(
                        get("/api/admin/categorias")
                                .header("Authorization", "Bearer " + token(1))
                                .param("activo", "false")
                                .param("q", nombre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void escriturasConcurrentesNoSobrescribenLaVersion() throws Exception {
        long categoria = categoria();
        long producto = producto(categoria, "Concurrente");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Callable<Integer> tarea =
                    () -> {
                        salida.await();
                        return estadoProducto(producto, false, true, 0).getStatus();
                    };
            Future<Integer> a = executor.submit(tarea);
            Future<Integer> b = executor.submit(tarea);
            salida.countDown();
            assertEquals(
                    Set.of(200, 409),
                    Set.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)));
            assertEquals(1, productos.findById(producto).orElseThrow().getVersion());
        } finally {
            executor.shutdownNow();
        }
    }
}
