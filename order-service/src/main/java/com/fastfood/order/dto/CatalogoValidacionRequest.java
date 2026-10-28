package com.fastfood.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CatalogoValidacionRequest {
    private List<Long> productoIds;
}
