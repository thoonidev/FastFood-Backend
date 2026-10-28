package com.fastfood.auth;

import com.fastfood.auth.dto.*;
import com.fastfood.auth.model.Usuario.Rol;
import com.fastfood.auth.model.Usuario.EstadoUsuario;
import com.fastfood.auth.repository.UsuarioRepository;
import com.fastfood.auth.service.UsuarioService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(properties={"eureka.client.enabled=false", "fastfood.demo.enabled=false",
    "spring.datasource.url=jdbc:mysql://localhost:3306/fastfood_auth_test_db?createDatabaseIfNotExist=true"})
@AutoConfigureMockMvc
class AutenticacionUsuariosTests {
    private static final String PASSWORD="Cliente123*";
    private static final String INTERNAL="fastfood_clave_interna_local_academica_2026_distinta_de_jwt_demo";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired UsuarioService service;
    private final Set<String> correos = new HashSet<>();

    private String correo() {
        String correo="prueba-"+UUID.randomUUID()+"@fastfood.test";
        correos.add(correo); return correo;
    }
    @AfterEach void limpiarRegistrosPropios() {
        for(String correo:correos) usuarios.findByCorreo(correo).ifPresent(u -> usuarios.deleteById(u.getId()));
    }
    private RegistroRequest datos(String correo, String password) {
        return new RegistroRequest("Ana", "Perez", correo, "999999999", password);
    }
    private Map<String, String> cuerpoRegistro(String correo, String password) {
        return Map.of("nombres", "Ana", "apellidos", "Perez", "correo", correo,
                "telefono", "999999999", "password", password);
    }
    private UsuarioResponse crear(Rol rol) { return service.registrar(datos(correo(), PASSWORD), rol); }
    private MockHttpServletResponse registrar(String correo, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(json.writeValueAsString(cuerpoRegistro(correo, password)))).andReturn().getResponse();
    }
    private MockHttpServletResponse login(String correo,String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("correo", correo, "password", password)))).andReturn().getResponse();
    }
    private String token(UsuarioResponse usuario) throws Exception {
        var respuesta=login(usuario.getCorreo(),PASSWORD);
        assertEquals(200,respuesta.getStatus(),respuesta.getContentAsString());
        return json.readTree(respuesta.getContentAsString()).get("token").asText();
    }
    private String tecnico(String sujeto,String destino,String scope,int segundos) {
        Instant ahora=Instant.now();
        return Jwts.builder().issuer("fastfood-internal").subject(sujeto).audience().add(destino).and()
                .claim("scope",scope).id(UUID.randomUUID().toString()).issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(segundos)))
                .signWith(Keys.hmacShaKeyFor(INTERNAL.getBytes(StandardCharsets.UTF_8)),Jwts.SIG.HS256).compact();
    }
    private MockHttpServletResponse cambiar(UsuarioResponse empleado,String admin,EstadoUsuario estado,long version) throws Exception {
        return mvc.perform(patch("/api/empleados/"+empleado.getId()+"/estado")
                .header("Authorization","Bearer "+admin).contentType("application/json")
                .content(json.writeValueAsString(new EstadoUsuarioRequest(estado,version))))
                .andReturn().getResponse();
    }
    @Test void registroNormalizaCorreoYNoPublicaPassword() throws Exception {
        String correo=correo();
        var respuesta=registrar("  "+correo.toUpperCase(Locale.ROOT)+"  ",PASSWORD);
        assertEquals(201,respuesta.getStatus(),respuesta.getContentAsString());
        var usuario=json.readTree(respuesta.getContentAsString());
        assertEquals(correo,usuario.get("correo").asText());
        assertEquals("CLIENTE",usuario.get("rol").asText());
        assertEquals(1,usuario.get("versionSeguridad").asLong());
        assertFalse(respuesta.getContentAsString().contains("password"));
        assertFalse(respuesta.getContentAsString().contains("token"));
        assertTrue(usuario.get("fechaCreacion").asText().endsWith("Z"));
        var persistido=usuarios.findByCorreo(correo).orElseThrow();
        assertTrue(persistido.getPasswordHash().startsWith("$2"));
        assertNotEquals(PASSWORD,persistido.getPasswordHash());
        assertFalse(persistido.toString().contains(persistido.getPasswordHash()));
        assertFalse(datos(correo, PASSWORD).toString().contains(PASSWORD));
        assertEquals(409,registrar(correo,PASSWORD).getStatus());
    }
    @Test void registroPublicoRechazaRolExtra() throws Exception {
        String correo=correo();
        var body=new LinkedHashMap<String,Object>();
        body.put("nombres","Ana");body.put("apellidos","Perez");body.put("telefono","999999999");
        body.put("correo",correo);body.put("password",PASSWORD);body.put("rol","ADMINISTRADOR");
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("JSON_INVALIDO"));
        assertFalse(usuarios.existsByCorreo(correo));
    }
    @Test void loginDevuelveBearerYPerfilSinHash() throws Exception {
        var usuario=crear(Rol.CLIENTE);var respuesta=login(usuario.getCorreo(),PASSWORD);
        assertEquals(200,respuesta.getStatus());
        var body=json.readTree(respuesta.getContentAsString());
        assertEquals("Bearer",body.get("tipo").asText());
        assertEquals(usuario.getId().longValue(),body.get("usuario").get("id").asLong());
        assertFalse(respuesta.getContentAsString().contains("passwordHash"));
        assertTrue(Instant.parse(body.get("expiresAt").asText()).isAfter(Instant.now().plusSeconds(3500)));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+body.get("token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(usuario.getCorreo()));
        assertEquals(401,login(usuario.getCorreo(),"Incorrecto123*").getStatus());
        assertEquals(login(usuario.getCorreo(),"Incorrecto123*").getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"",""),
                login(correo(),PASSWORD).getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"",""));
    }
    @Test void passwordRespetaBytesUtf8YSusEspacios() throws Exception {
        String correo=correo();String password="á".repeat(36);
        assertEquals(201,registrar(correo,password).getStatus());
        assertEquals(200,login(correo,password).getStatus());
        String largo=correo();
        assertEquals(400,registrar(largo,"á".repeat(37)).getStatus());
        assertFalse(usuarios.existsByCorreo(largo));
        String conEspacios=correo();
        assertEquals(201,registrar(conEspacios," Cliente123* ").getStatus());
        assertEquals(200,login(conEspacios," Cliente123* ").getStatus());
        assertEquals(401,login(conEspacios,PASSWORD).getStatus());
    }
    @Test void perfilSoloModificaDatosPersonales() throws Exception {
        var usuario=crear(Rol.CLIENTE);String token=token(usuario);
        mvc.perform(put("/api/usuarios/me").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"nombres\":\"Rosa\",\"apellidos\":\"Lopez\",\"telefono\":\"987654321\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombres").value("Rosa"))
                .andExpect(jsonPath("$.correo").value(usuario.getCorreo())).andExpect(jsonPath("$.versionSeguridad").value(1));
        mvc.perform(put("/api/usuarios/me").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"nombres\":\"Rosa\",\"apellidos\":\"Lopez\",\"telefono\":\"987654321\",\"rol\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(Rol.CLIENTE,usuarios.findById(usuario.getId()).orElseThrow().getRol());
    }
    @Test void soloAdministradorGestionaEmpleadosYConsultaUsuarios() throws Exception {
        var admin=crear(Rol.ADMINISTRADOR);String adminToken=token(admin);
        for(Rol rol:List.of(Rol.CLIENTE,Rol.EMPLEADO)) {
            String token=token(crear(rol));
            mvc.perform(get("/api/usuarios").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/empleados").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            mvc.perform(get("/actuator/health").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        }
        String correo=correo();
        mvc.perform(post("/api/empleados").header("Authorization","Bearer "+adminToken).contentType("application/json")
                .content(json.writeValueAsString(cuerpoRegistro(correo, PASSWORD))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("EMPLEADO"));
        mvc.perform(get("/api/usuarios").param("rol","EMPLEADO").param("q",correo)
                .header("Authorization","Bearer "+adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].correo").value(correo));
        mvc.perform(get("/api/empleados").param("size","101").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/actuator/health").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
    }
    @Test void cambioEstadoRevocaTokensInclusoDespuesDeReactivar() throws Exception {
        String admin=token(crear(Rol.ADMINISTRADOR));var empleado=crear(Rol.EMPLEADO);String viejo=token(empleado);
        assertEquals(200,cambiar(empleado,admin,EstadoUsuario.INACTIVO,1).getStatus());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+viejo))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USUARIO_INACTIVO"));
        assertEquals(401,login(empleado.getCorreo(),PASSWORD).getStatus());
        assertEquals(409,cambiar(empleado,admin,EstadoUsuario.ACTIVO,1).getStatus());
        assertEquals(200,cambiar(empleado,admin,EstadoUsuario.ACTIVO,2).getStatus());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+viejo))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESION_VENCIDA"));
        String nuevo=token(empleado);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+nuevo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionSeguridad").value(3));
    }
    @Test void administradorNoPuedeModificarClienteComoEmpleado() throws Exception {
        String admin=token(crear(Rol.ADMINISTRADOR));var cliente=crear(Rol.CLIENTE);
        assertEquals(404,cambiar(cliente,admin,EstadoUsuario.INACTIVO,1).getStatus());
        assertEquals(EstadoUsuario.ACTIVO,usuarios.findById(cliente.getId()).orElseThrow().getEstado());
    }
    @Test void dosAltasConcurrentesCreanUnSoloUsuario() throws Exception {
        String correo=correo();ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch salida=new CountDownLatch(1);
        try {
            Callable<Integer> tarea=()->{salida.await();return registrar(correo,PASSWORD).getStatus();};
            Future<Integer> a=executor.submit(tarea),b=executor.submit(tarea);salida.countDown();
            assertEquals(Set.of(201,409),Set.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS)));
            assertTrue(usuarios.existsByCorreo(correo));
        } finally { executor.shutdownNow(); }
    }
    @Test void cambiosConcurrentesDeEstadoNoPierdenLaVersion() throws Exception {
        String admin=token(crear(Rol.ADMINISTRADOR));var empleado=crear(Rol.EMPLEADO);
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch salida=new CountDownLatch(1);
        try {
            Callable<Integer> tarea=()->{salida.await();return cambiar(empleado,admin,EstadoUsuario.INACTIVO,1).getStatus();};
            Future<Integer> a=executor.submit(tarea),b=executor.submit(tarea);salida.countDown();
            assertEquals(Set.of(200,409),Set.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS)));
            assertEquals(2,usuarios.findById(empleado.getId()).orElseThrow().getVersionSeguridad());
        } finally { executor.shutdownNow(); }
    }
    @Test void estadoInternoSoloAdmiteServicioDestinoYScopeCorrectos() throws Exception {
        var usuario=crear(Rol.CLIENTE);String ruta="/internal/auth/usuarios/"+usuario.getId()+"/estado";
        mvc.perform(get(ruta).header("Authorization","Bearer "+tecnico("catalog-service","auth-service","USUARIO_ESTADO",60)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(usuario.getId()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        for(String token:List.of(token(usuario),
                tecnico("intruso","auth-service","USUARIO_ESTADO",60),
                tecnico("order-service","catalog-service","USUARIO_ESTADO",60),
                tecnico("socket-server","auth-service","OTRO",60),
                tecnico("catalog-service","auth-service","USUARIO_ESTADO",61))) {
            mvc.perform(get(ruta).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+tecnico("order-service","auth-service","USUARIO_ESTADO",60)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("X-Usuario",usuario.getId()).header("X-Rol","ADMINISTRADOR"))
                .andExpect(status().isUnauthorized());
    }
    @Test void inicializadorAdminEsIdempotenteYNoConvierteOtraCuenta() throws Exception {
        var admin=crear(Rol.ADMINISTRADOR);
        String hash=usuarios.findById(admin.getId()).orElseThrow().getPasswordHash();
        service.crearAdminDemo(admin.getCorreo(),"OtraPassword123*");
        assertEquals(hash,usuarios.findById(admin.getId()).orElseThrow().getPasswordHash());
        var cliente=crear(Rol.CLIENTE);
        assertThrows(IllegalStateException.class,()->service.crearAdminDemo(cliente.getCorreo(),PASSWORD));
        assertEquals(Rol.CLIENTE,usuarios.findById(cliente.getId()).orElseThrow().getRol());
    }
}
