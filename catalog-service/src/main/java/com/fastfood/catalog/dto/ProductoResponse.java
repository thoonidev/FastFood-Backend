package com.fastfood.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fastfood.catalog.model.Producto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductoResponse {

    private Long id;
    private CategoriaResumen categoria;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private String imagenUrl;
    private boolean disponible;
    private boolean destacado;
    private Long version;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean activo;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long versionCategoria;

    public static ProductoResponse de(Producto producto, boolean administrador) {
        ProductoResponse dto = new ProductoResponse();
        dto.setId(producto.getId());
        dto.setCategoria(
                new CategoriaResumen(
                        producto.getCategoria().getId(), producto.getCategoria().getNombre()));
        dto.setNombre(producto.getNombre());
        dto.setDescripcion(producto.getDescripcion());
        dto.setPrecio(producto.getPrecio());
        dto.setImagenUrl(producto.getImagenUrl());
        dto.setDisponible(producto.isDisponible());
        dto.setDestacado(producto.isDestacado());
        dto.setVersion(producto.getVersion());
        if (administrador) {
            dto.setActivo(producto.isActivo());
            dto.setVersionCategoria(producto.getCategoria().getVersion());
        }
        return dto;
    }

    @Data
    @AllArgsConstructor
    public static class CategoriaResumen {
        private Long id;
        private String nombre;
    }
}
