package com.fastfood.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CatalogoValidacionResponse {

    private List<ProductoSnapshot> productos;

    @Data
    public static class ProductoSnapshot {
        private Long id;
        private Long categoriaId;
        private String nombre;
        private BigDecimal precio;
        private Boolean activo;
        private Boolean disponible;
        private Boolean categoriaActiva;
        private Long versionProducto;
        private Long versionCategoria;
    }
}
