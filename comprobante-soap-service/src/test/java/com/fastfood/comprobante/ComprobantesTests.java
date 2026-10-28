package com.fastfood.comprobante;

import com.fastfood.comprobante.exception.ComprobanteException;
import com.fastfood.comprobante.service.ComprobanteService;
import com.fastfood.comprobante.soap.contract.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.JdkHttpClientMessageSender;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "spring.jpa.show-sql=false",
        "spring.datasource.url=jdbc:mysql://localhost:3306/fastfood_comprobante_test_db?createDatabaseIfNotExist=true"})
class ComprobantesTests {
    @Value("${local.server.port}") int port;
    @Autowired ComprobanteService service;
    @Autowired Jaxb2Marshaller marshaller;
    @Autowired JdbcTemplate jdbc;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    private final Set<Long> propios = ConcurrentHashMap.newKeySet();
    private WebServiceTemplate soap;

    @BeforeEach
    void clienteReal() throws Exception {
        soap = new WebServiceTemplate(marshaller);
        soap.setDefaultUri("http://localhost:" + port + "/ws");
        JdkHttpClientMessageSender sender = new JdkHttpClientMessageSender(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        sender.setRequestTimeout(Duration.ofSeconds(3));
        sender.afterPropertiesSet();
        soap.setMessageSender(sender);
    }

    @AfterEach
    void limpiarSoloPropios() {
        for (Long id : propios) {
            jdbc.update("DELETE d FROM comprobante_detalles d JOIN comprobantes c ON c.id=d.comprobante_id WHERE c.pedido_id=?", id);
            jdbc.update("DELETE FROM comprobantes WHERE pedido_id=?", id);
        }
    }

    @Test
    void emisionConsultaYReintentoPorSoapRealConservanElComprobante() {
        Venta venta = venta();
        GenerarComprobanteResponse primera = generar(venta);
        assertTrue(primera.isCreado());
        assertEquals("FFA-" + String.format(Locale.ROOT, "%06d", venta.getPedidoId()), primera.getComprobante().getNumero());
        GenerarComprobanteResponse repetida = generar(venta);
        assertFalse(repetida.isCreado());
        assertDocumentoIgual(primera.getComprobante(), repetida.getComprobante());
        ConsultarComprobanteRequest consulta = new ConsultarComprobanteRequest();
        consulta.setPedidoId(venta.getPedidoId());
        assertDocumentoIgual(primera.getComprobante(), ((ConsultarComprobanteResponse) enviar(consulta, "consultarComprobante")).getComprobante());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE pedido_id=?", Long.class, venta.getPedidoId()));
    }

    @Test
    void llamadasConcurrentesGeneranSoloUnComprobante() throws Exception {
        long id = nuevoId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Callable<GenerarComprobanteResponse> tarea = () -> { salida.await(); return service.generar(venta(id)); };
            Future<GenerarComprobanteResponse> a = pool.submit(tarea), b = pool.submit(tarea);
            salida.countDown();
            var ra = a.get(10, TimeUnit.SECONDS); var rb = b.get(10, TimeUnit.SECONDS);
            assertNotEquals(ra.isCreado(), rb.isCreado());
            assertDocumentoIgual(ra.getComprobante(), rb.getComprobante());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void ventaDistintaNoReemplazaElDocumentoHistorico() {
        Venta venta = venta();
        var original = generar(venta);
        venta.setClienteNombres("Otra persona");
        SoapFaultClientException fault = assertThrows(SoapFaultClientException.class, () -> generar(venta));
        assertEquals("Client", fault.getFaultCode().getLocalPart());
        assertEquals("DATOS_COMPROBANTE_CONFLICTO", codigo(fault));
        assertDocumentoIgual(original.getComprobante(), service.consultar(venta.getPedidoId()).getComprobante());
    }

    @Test
    void consultaInexistenteTieneFaultDeNegocio() {
        ConsultarComprobanteRequest request = new ConsultarComprobanteRequest(); request.setPedidoId(nuevoId());
        var fault = assertThrows(SoapFaultClientException.class, () -> enviar(request, "consultarComprobante"));
        assertEquals("COMPROBANTE_NO_ENCONTRADO", codigo(fault));
    }

    @Test
    void montosInconsistentesSonRechazadosPorSoap() {
        Venta venta = venta(); venta.setTotal(new BigDecimal("26.00"));
        var fault = assertThrows(SoapFaultClientException.class, () -> generar(venta));
        assertEquals("VALIDACION", codigo(fault));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM comprobantes WHERE pedido_id=?", Long.class, venta.getPedidoId()));
    }

    @Test
    void cantidadesIdentidadMetodoYDecimalesSonValidados() {
        for (int caso = 0; caso < 5; caso++) {
            Venta venta = venta();
            switch (caso) {
                case 0 -> venta.setClienteId(0L);
                case 1 -> venta.getDetalle().get(0).setCantidad(0);
                case 2 -> venta.setMetodoPago("TRANSFERENCIA");
                case 3 -> venta.getDetalle().get(0).setPrecioUnitario(new BigDecimal("12.501"));
                case 4 -> venta.setImpuesto(new BigDecimal("1.00"));
            }
            assertEquals("VALIDACION", assertThrows(ComprobanteException.class, () -> service.generar(venta)).getCode());
        }
    }

    @Test
    void xmlFueraDelContratoTieneFaultClientSeguro() throws Exception {
        var response = http("POST", "/ws", "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body>"
                + "<consultarComprobanteRequest xmlns='urn:fastfood:comprobantes:v1'><otro>1</otro></consultarComprobanteRequest>"
                + "</s:Body></s:Envelope>", token("COMPROBANTE_OPERAR", "comprobante-soap-service"));
        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("VALIDACION"));
        assertFalse(response.body().contains("SAXParseException"));
    }

    @Test
    void wsdlEsPublicoYPublicaElContratoYLasAcciones() throws Exception {
        assertEquals(200, http("GET", "/ws/comprobantes.wsdl", null, null).statusCode());
        assertEquals(401, http("POST", "/ws", "", null).statusCode());
        assertEquals(401, http("GET", "/actuator/health", null, null).statusCode());
        var response = http("GET", "/ws/comprobantes.wsdl", null, token("COMPROBANTE_OPERAR", "comprobante-soap-service"));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ComprobantesPort"));
        assertTrue(response.body().contains("urn:fastfood:comprobantes:v1:generarComprobante"));
        assertTrue(response.body().contains("http://localhost:" + port + "/ws"));
    }

    @Test
    void audienciaScopeYDiagnosticoSeControlanPorSeparado() throws Exception {
        assertEquals(401, http("GET", "/ws/comprobantes.wsdl", null, token("COMPROBANTE_OPERAR", "socket-server")).statusCode());
        assertEquals(403, http("POST", "/ws", "", token("OBSERVABILIDAD", "comprobante-soap-service")).statusCode());
        assertEquals(403, http("GET", "/actuator/health", null, token("COMPROBANTE_OPERAR", "comprobante-soap-service")).statusCode());
        assertEquals(200, http("GET", "/actuator/health", null, token("OBSERVABILIDAD", "comprobante-soap-service")).statusCode());
    }

    @Test
    void entidadesExternasNoSeResuelven() throws Exception {
        var response = http("POST", "/ws", "<?xml version='1.0'?><!DOCTYPE x [<!ENTITY dato SYSTEM 'file:///C:/Windows/win.ini'>]>"
                + "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body>"
                + "<consultarComprobanteRequest xmlns='urn:fastfood:comprobantes:v1'><pedidoId>&dato;</pedidoId></consultarComprobanteRequest>"
                + "</s:Body></s:Envelope>", token("COMPROBANTE_OPERAR", "comprobante-soap-service"));
        assertTrue(response.statusCode() >= 400);
        assertFalse(response.body().contains("[fonts]"));
        assertFalse(response.body().contains("SAXParseException"));
    }

    @Test
    void ambosServiciosUsanElMismoXsdYElXmlCumpleSuOrden() throws Exception {
        assertArrayEquals(Files.readAllBytes(Path.of("src/main/resources/soap/comprobantes-v1.xsd")),
                Files.readAllBytes(Path.of("../order-service/src/main/resources/soap/comprobantes-v1.xsd")));
        GenerarComprobanteRequest request = new GenerarComprobanteRequest(); request.setVenta(venta());
        StringWriter xml = new StringWriter(); marshaller.marshal(request, new StreamResult(xml));
        var factory = javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.newSchema(Path.of("src/main/resources/soap/comprobantes-v1.xsd").toFile()).newValidator()
                .validate(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml.toString())));
    }

    private void assertDocumentoIgual(Comprobante esperado, Comprobante actual) {
        assertEquals(esperado.getId(), actual.getId());
        assertEquals(esperado.getNumero(), actual.getNumero());
        assertEquals(esperado.getFechaEmision(), actual.getFechaEmision());
        assertEquals(json.valueToTree(esperado.getVenta()), json.valueToTree(actual.getVenta()));
    }

    @Test
    void huellaConservaElFormatoDeComprobantesAnteriores() throws Exception {
        String anterior = Files.readString(Path.of("src/test/resources/soap/venta-anterior.json")).trim();
        String actual = json.writeValueAsString(com.fastfood.comprobante.validation.VentaValidacion.de(venta(123456789L)));
        assertEquals(anterior, actual);
    }

    private long nuevoId() { long id = 100000000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1000000000L); propios.add(id); return id; }
    private Venta venta() { return venta(nuevoId()); }
    private Venta venta(long id) {
        Venta venta = new Venta(); venta.setPedidoId(id); venta.setClienteId(1L);
        venta.setClienteNombres("Ana Perez"); venta.setClienteCorreo("ana@fastfood.test"); venta.setMetodoPago("TARJETA");
        venta.setSubtotal(new BigDecimal("25.00")); venta.setImpuesto(new BigDecimal("0.00")); venta.setTotal(new BigDecimal("25.00"));
        Detalle detalle = new Detalle(); detalle.setProductoId(1L); detalle.setNombreProducto("Hamburguesa");
        detalle.setCantidad(2); detalle.setPrecioUnitario(new BigDecimal("12.50")); detalle.setSubtotal(new BigDecimal("25.00"));
        detalle.setObservacion("Sin cebolla"); venta.getDetalle().add(detalle); return venta;
    }
    private GenerarComprobanteResponse generar(Venta venta) { GenerarComprobanteRequest request = new GenerarComprobanteRequest(); request.setVenta(venta); return (GenerarComprobanteResponse) enviar(request, "generarComprobante"); }
    private Object enviar(Object request, String accion) {
        return soap.marshalSendAndReceive(request, message -> {
            new SoapActionCallback("urn:fastfood:comprobantes:v1:" + accion).doWithMessage(message);
            ((HeadersAwareSenderWebServiceConnection) TransportContextHolder.getTransportContext().getConnection())
                    .addRequestHeader("Authorization", "Bearer " + token("COMPROBANTE_OPERAR", "comprobante-soap-service"));
        });
    }
    private String codigo(SoapFaultClientException fault) {
        var entries = fault.getSoapFault().getFaultDetail().getDetailEntries();
        while (entries.hasNext()) { var element = entries.next(); if ("code".equals(element.getName().getLocalPart()))
            return ((javax.xml.transform.dom.DOMSource) element.getSource()).getNode().getTextContent(); }
        fail("Falta codigo en Fault"); return null;
    }
    private HttpResponse<String> http(String metodo, String ruta, String body, String jwt) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ruta)).timeout(Duration.ofSeconds(5));
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        if (body != null) request.header("Content-Type", "text/xml; charset=UTF-8");
        return HttpClient.newHttpClient().send(request.method(metodo, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(String scope, String destino) {
        Instant ahora = Instant.now();
        return Jwts.builder().issuer("fastfood-internal").subject("order-service").audience().add(destino).and()
                .claim("scope", scope).id(UUID.randomUUID().toString()).issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(60))).signWith(Keys.hmacShaKeyFor(
                        "fastfood_clave_interna_local_academica_2026_distinta_de_jwt_demo".getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
}
