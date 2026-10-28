package com.fastfood.catalog.service;

import com.fastfood.catalog.dto.*;
import com.fastfood.catalog.exception.NegocioException;
import com.fastfood.catalog.model.*;
import com.fastfood.catalog.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;

    @Transactional(readOnly = true)
    public PaginaResponse<ProductoResponse> listar(
            boolean administrador,
            Boolean activo,
            Boolean disponible,
            Long categoriaId,
            Boolean destacado,
            String q,
            int page,
            int size) {
        CategoriaService.validarFiltros(q, page, size);
        if (categoriaId != null && categoriaId <= 0) {
            throw new NegocioException(400, "VALIDACION", "La categoria debe ser un ID positivo.");
        }
        Specification<Producto> filtro = (root, query, cb) -> cb.conjunction();
        if (!administrador) {
            filtro =
                    filtro.and(
                            (root, query, cb) ->
                                    cb.and(
                                            cb.isTrue(root.get("activo")),
                                            cb.isTrue(root.get("categoria").get("activo"))));
        } else if (activo != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("activo"), activo));
        }
        if (disponible != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("disponible"), disponible));
        }
        if (categoriaId != null) {
            filtro =
                    filtro.and(
                            (root, query, cb) ->
                                    cb.equal(root.get("categoria").get("id"), categoriaId));
        }
        if (destacado != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("destacado"), destacado));
        }
        if (q != null && !q.isBlank()) {
            String patron = "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            filtro =
                    filtro.and(
                            (root, query, cb) ->
                                    cb.or(
                                            cb.like(cb.lower(root.get("nombre")), patron),
                                            cb.like(cb.lower(root.get("descripcion")), patron)));
        }
        Page<Producto> pagina =
                productos.findAll(filtro, PageRequest.of(page, size, Sort.by("nombre", "id")));
        return PaginaResponse.de(
                pagina.map(producto -> ProductoResponse.de(producto, administrador)));
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id, boolean administrador) {
        Producto producto = buscar(id);
        if (!administrador && (!producto.isActivo() || !producto.getCategoria().isActivo())) {
            throw noEncontrado();
        }
        return ProductoResponse.de(producto, administrador);
    }

    @Transactional
    public ProductoResponse crear(ProductoCrearRequest request) {
        Producto producto = new Producto();
        producto.setCategoria(obtenerCategoria(request.getCategoriaId()));
        producto.setNombre(request.getNombre().trim());
        producto.setDescripcion(request.getDescripcion().trim());
        producto.setPrecio(request.getPrecio().setScale(2));
        producto.setImagenUrl(validarImagen(request.getImagenUrl()));
        producto.setDestacado(Boolean.TRUE.equals(request.getDestacado()));
        return ProductoResponse.de(productos.saveAndFlush(producto), true);
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoActualizarRequest request) {
        Producto producto = buscar(id);
        comprobarVersion(producto, request.getVersionEsperada());
        producto.setCategoria(obtenerCategoria(request.getCategoriaId()));
        producto.setNombre(request.getNombre().trim());
        producto.setDescripcion(request.getDescripcion().trim());
        producto.setPrecio(request.getPrecio().setScale(2));
        producto.setImagenUrl(validarImagen(request.getImagenUrl()));
        producto.setDestacado(request.getDestacado());
        producto.setFechaActualizacion(Instant.now());
        return ProductoResponse.de(productos.saveAndFlush(producto), true);
    }

    @Transactional
    public ProductoResponse cambiarEstado(Long id, ProductoEstadoRequest request) {
        Producto producto = buscar(id);
        comprobarVersion(producto, request.getVersionEsperada());
        producto.setActivo(request.getActivo());
        producto.setDisponible(request.getDisponible());
        producto.setFechaActualizacion(Instant.now());
        return ProductoResponse.de(productos.saveAndFlush(producto), true);
    }

    private Producto buscar(Long id) {
        return productos.findById(id).orElseThrow(this::noEncontrado);
    }

    private Categoria obtenerCategoria(Long id) {
        return categorias
                .findById(id)
                .orElseThrow(
                        () ->
                                new NegocioException(
                                        404, "RECURSO_NO_ENCONTRADO", "La categoria no existe."));
    }

    private void comprobarVersion(Producto producto, Long versionEsperada) {
        if (!Objects.equals(producto.getVersion(), versionEsperada)) {
            throw new NegocioException(
                    409,
                    "VERSION_DESACTUALIZADA",
                    "El producto fue modificado. Actualiza los datos.");
        }
    }

    private String validarImagen(String url) {
        try {
            java.net.URI imagen = java.net.URI.create(url.trim());
            if (imagen.getHost() != null
                    && ("http".equalsIgnoreCase(imagen.getScheme())
                            || "https".equalsIgnoreCase(imagen.getScheme()))) {
                return imagen.toString();
            }
        } catch (IllegalArgumentException ex) {
            // La URL solo se guarda; el servidor no descarga la imagen.
        }
        throw new NegocioException(
                400, "VALIDACION", "La imagen debe tener una URL http o https valida.");
    }

    private NegocioException noEncontrado() {
        return new NegocioException(404, "RECURSO_NO_ENCONTRADO", "El producto no existe.");
    }
}
