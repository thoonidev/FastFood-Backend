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
public class CategoriaService {

    private final CategoriaRepository categorias;

    @Transactional(readOnly = true)
    public PaginaResponse<CategoriaResponse> listar(
            boolean administrador, Boolean activo, String q, int page, int size) {
        validarFiltros(q, page, size);
        Specification<Categoria> filtro = (root, query, cb) -> cb.conjunction();
        Boolean estado = administrador ? activo : Boolean.TRUE;
        if (estado != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("activo"), estado));
        }
        if (q != null && !q.isBlank()) {
            String patron = "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            filtro = filtro.and((root, query, cb) -> cb.like(cb.lower(root.get("nombre")), patron));
        }
        Page<Categoria> pagina =
                categorias.findAll(filtro, PageRequest.of(page, size, Sort.by("nombre", "id")));
        return PaginaResponse.de(pagina.map(CategoriaResponse::de));
    }

    @Transactional
    public CategoriaResponse crear(CategoriaCrearRequest request) {
        String nombre = request.getNombre().trim();
        if (categorias.existsByNombreIgnoreCase(nombre)) {
            throw duplicado();
        }
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setDescripcion(limpiar(request.getDescripcion()));
        return CategoriaResponse.de(categorias.saveAndFlush(categoria));
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaActualizarRequest request) {
        Categoria categoria = obtener(id);
        comprobarVersion(categoria, request.getVersionEsperada());
        String nombre = request.getNombre().trim();
        if (categorias.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw duplicado();
        }
        categoria.setNombre(nombre);
        categoria.setDescripcion(limpiar(request.getDescripcion()));
        categoria.setFechaActualizacion(Instant.now());
        return CategoriaResponse.de(categorias.saveAndFlush(categoria));
    }

    @Transactional
    public CategoriaResponse cambiarEstado(Long id, CategoriaEstadoRequest request) {
        Categoria categoria = obtener(id);
        comprobarVersion(categoria, request.getVersionEsperada());
        categoria.setActivo(request.getActivo());
        categoria.setFechaActualizacion(Instant.now());
        return CategoriaResponse.de(categorias.saveAndFlush(categoria));
    }

    private Categoria obtener(Long id) {
        return categorias
                .findById(id)
                .orElseThrow(
                        () ->
                                new NegocioException(
                                        404, "RECURSO_NO_ENCONTRADO", "La categoria no existe."));
    }

    private void comprobarVersion(Categoria categoria, Long versionEsperada) {
        if (!Objects.equals(categoria.getVersion(), versionEsperada)) {
            throw new NegocioException(
                    409,
                    "VERSION_DESACTUALIZADA",
                    "La categoria fue modificada. Actualiza los datos.");
        }
    }

    private String limpiar(String texto) {
        return texto == null ? null : texto.trim();
    }

    private NegocioException duplicado() {
        return new NegocioException(
                409, "CATEGORIA_DUPLICADA", "Ya existe una categoria con ese nombre.");
    }

    public static void validarFiltros(String q, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 100)) {
            throw new NegocioException(
                    400, "VALIDACION", "Los filtros o la paginacion no son validos.");
        }
    }
}
