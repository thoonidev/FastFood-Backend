package com.fastfood.catalog.dto;

import com.fastfood.catalog.model.Categoria;

import lombok.Data;

import java.time.Instant;

@Data
public class CategoriaResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private boolean activo;
    private Long version;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;

    public static CategoriaResponse de(Categoria categoria) {
        CategoriaResponse dto = new CategoriaResponse();
        dto.setId(categoria.getId());
        dto.setNombre(categoria.getNombre());
        dto.setDescripcion(categoria.getDescripcion());
        dto.setActivo(categoria.isActivo());
        dto.setVersion(categoria.getVersion());
        dto.setFechaCreacion(categoria.getFechaCreacion());
        dto.setFechaActualizacion(categoria.getFechaActualizacion());
        return dto;
    }
}
