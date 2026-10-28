package com.fastfood.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CatalogoValidacionResponse {
    private List<ProductoSnapshot> productos;
}
