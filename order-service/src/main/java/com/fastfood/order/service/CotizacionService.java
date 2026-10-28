package com.fastfood.order.service;

import com.fastfood.order.dto.*;
import com.fastfood.order.dto.CatalogoValidacionResponse.ProductoSnapshot;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.feign.CatalogFeignClient;
import com.fastfood.order.model.*;
import com.fastfood.order.repository.CotizacionRepository;
import com.fastfood.order.security.JwtUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CotizacionService {

    public static final BigDecimal MAXIMO = new BigDecimal("9999999999.99");

    private final CotizacionRepository cotizaciones;
    private final CatalogFeignClient catalogo;
    private final JwtUtil jwt;
    private final TransactionTemplate transaccion;

    public CotizacionService(
            CotizacionRepository cotizaciones,
            CatalogFeignClient catalogo,
            JwtUtil jwt,
            PlatformTransactionManager manager) {
        this.cotizaciones = cotizaciones;
        this.catalogo = catalogo;
        this.jwt = jwt;
        transaccion = new TransactionTemplate(manager);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public CotizacionResponse crear(Long clienteId, CotizacionRequest request) {
        List<CotizacionRequest.Linea> lineas = agrupar(request.getDetalles());
        List<Long> ids =
                lineas.stream().map(CotizacionRequest.Linea::getProductoId).distinct().toList();
        Map<Long, ProductoSnapshot> oferta = consultarCatalogo(ids);
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setClienteId(clienteId);
        cotizacion.setHuellaCatalogo(huellaCatalogo(oferta));
        BigDecimal total = new BigDecimal("0.00");

        for (CotizacionRequest.Linea linea : lineas) {
            ProductoSnapshot producto = oferta.get(linea.getProductoId());
            CotizacionDetalle detalle = new CotizacionDetalle();
            detalle.setCotizacion(cotizacion);
            detalle.setProductoId(producto.getId());
            detalle.setCategoriaId(producto.getCategoriaId());
            detalle.setNombreProducto(producto.getNombre());
            detalle.setPrecioUnitario(producto.getPrecio().setScale(2));
            detalle.setCantidad(linea.getCantidad());
            detalle.setObservacion(linea.getObservacion());
            detalle.setVersionProducto(producto.getVersionProducto());
            detalle.setVersionCategoria(producto.getVersionCategoria());
            detalle.setSubtotal(
                    detalle.getPrecioUnitario()
                            .multiply(BigDecimal.valueOf(linea.getCantidad()))
                            .setScale(2));
            total = total.add(detalle.getSubtotal());
            cotizacion.getDetalles().add(detalle);
        }
        if (total.compareTo(MAXIMO) > 0) {
            throw new NegocioException(400, "VALIDACION", "El total supera el importe permitido.");
        }
        cotizacion.setTotal(total.setScale(2));
        return transaccion.execute(
                status -> {
                    Instant ahora = Instant.now();
                    cotizacion.setCreadaEn(ahora);
                    cotizacion.setExpiraEn(ahora.plusSeconds(300));
                    return CotizacionResponse.de(cotizaciones.saveAndFlush(cotizacion));
                });
    }

    public Cotizacion obtenerParaRegistro(UUID id, Long clienteId) {
        return transaccion.execute(
                status -> {
                    Cotizacion cotizacion =
                            cotizaciones
                                    .findById(id)
                                    .orElseThrow(
                                            () ->
                                                    new NegocioException(
                                                            404,
                                                            "RECURSO_NO_ENCONTRADO",
                                                            "La cotizacion no existe."));
                    exigirPropiedad(cotizacion, clienteId);
                    cotizacion.getDetalles().size();
                    return cotizacion;
                });
    }

    public Map<Long, ProductoSnapshot> consultarCatalogo(List<Long> ids) {
        CatalogoValidacionResponse respuesta =
                catalogo.validar(
                        new CatalogoValidacionRequest(ids),
                        "Bearer " + jwt.generarTecnicoCatalogo());
        if (respuesta == null
                || respuesta.getProductos() == null
                || respuesta.getProductos().size() != ids.size()) {
            throw respuestaInvalida();
        }
        Map<Long, ProductoSnapshot> resultado = new HashMap<>();
        for (ProductoSnapshot producto : respuesta.getProductos()) {
            if (producto == null
                    || producto.getId() == null
                    || !ids.contains(producto.getId())
                    || resultado.containsKey(producto.getId())
                    || producto.getCategoriaId() == null
                    || producto.getCategoriaId() <= 0
                    || producto.getNombre() == null
                    || producto.getNombre().isBlank()
                    || producto.getNombre().length() > 100
                    || producto.getPrecio() == null
                    || producto.getPrecio().signum() <= 0
                    || producto.getPrecio().scale() > 2
                    || producto.getPrecio().compareTo(MAXIMO) > 0
                    || producto.getVersionProducto() == null
                    || producto.getVersionProducto() < 0
                    || producto.getVersionCategoria() == null
                    || producto.getVersionCategoria() < 0
                    || !Boolean.TRUE.equals(producto.getActivo())
                    || !Boolean.TRUE.equals(producto.getDisponible())
                    || !Boolean.TRUE.equals(producto.getCategoriaActiva())) {
                throw respuestaInvalida();
            }
            resultado.put(producto.getId(), producto);
        }
        return resultado;
    }

    public static String huellaCatalogo(Map<Long, ProductoSnapshot> oferta) {
        String contenido =
                oferta.values().stream()
                        .sorted(Comparator.comparing(ProductoSnapshot::getId))
                        .map(
                                p ->
                                        p.getId()
                                                + ":"
                                                + p.getCategoriaId()
                                                + ":"
                                                + p.getVersionProducto()
                                                + ":"
                                                + p.getVersionCategoria()
                                                + ":"
                                                + p.getPrecio().setScale(2).toPlainString())
                        .collect(Collectors.joining("|"));
        return IdempotenciaService.huella(contenido);
    }

    public static void exigirPropiedad(Cotizacion cotizacion, Long clienteId) {
        if (!cotizacion.getClienteId().equals(clienteId)) {
            throw new NegocioException(
                    403, "ACCESO_DENEGADO", "La cotizacion pertenece a otro cliente.");
        }
    }

    public static void exigirVigencia(Cotizacion cotizacion) {
        if (!cotizacion.getExpiraEn().isAfter(Instant.now())) {
            throw new NegocioException(
                    409, "COTIZACION_EXPIRADA", "La cotizacion vencio. Vuelve a cotizar.");
        }
    }

    private List<CotizacionRequest.Linea> agrupar(List<CotizacionRequest.Linea> entrada) {
        Map<Long, Map<String, Integer>> grupos = new TreeMap<>();
        for (CotizacionRequest.Linea linea : entrada) {
            String observacion =
                    linea.getObservacion() == null ? "" : linea.getObservacion().trim();
            Map<String, Integer> observaciones =
                    grupos.computeIfAbsent(linea.getProductoId(), id -> new TreeMap<>());
            int cantidad = observaciones.getOrDefault(observacion, 0) + linea.getCantidad();
            if (cantidad > 99) {
                throw new NegocioException(
                        400, "VALIDACION", "Una linea agrupada no puede superar 99 unidades.");
            }
            observaciones.put(observacion, cantidad);
        }
        List<CotizacionRequest.Linea> resultado = new ArrayList<>();
        grupos.forEach(
                (id, observaciones) ->
                        observaciones.forEach(
                                (observacion, cantidad) -> {
                                    CotizacionRequest.Linea linea = new CotizacionRequest.Linea();
                                    linea.setProductoId(id);
                                    linea.setObservacion(observacion);
                                    linea.setCantidad(cantidad);
                                    resultado.add(linea);
                                }));
        return resultado;
    }

    private NegocioException respuestaInvalida() {
        return new NegocioException(
                503, "DEPENDENCIA_NO_DISPONIBLE", "La respuesta del catalogo no es valida.");
    }
}
