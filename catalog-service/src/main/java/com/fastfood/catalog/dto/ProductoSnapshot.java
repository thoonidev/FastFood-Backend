package com.fastfood.catalog.dto;

import com.fastfood.catalog.model.Producto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductoSnapshot {

    private Long id;
    private Long categoriaId;
    private String nombre;
    private BigDecimal precio;
    private boolean activo;
    private boolean disponible;
    private boolean categoriaActiva;
    private Long versionProducto;
    private Long versionCategoria;

    public static ProductoSnapshot de(Producto producto) {
        ProductoSnapshot dto = new ProductoSnapshot();
        dto.setId(producto.getId());
        dto.setCategoriaId(producto.getCategoria().getId());
        dto.setNombre(producto.getNombre());
        dto.setPrecio(producto.getPrecio());
        dto.setActivo(producto.isActivo());
        dto.setDisponible(producto.isDisponible());
        dto.setCategoriaActiva(producto.getCategoria().isActivo());
        dto.setVersionProducto(producto.getVersion());
        dto.setVersionCategoria(producto.getCategoria().getVersion());
        return dto;
    }
}
