package com.fastfood.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fastfood.order.model.*;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DetallePedidoResponse {

    private Long productoId;
    private Long categoriaId;
    private String nombreProducto;
    private BigDecimal precioUnitario;
    private Integer cantidad;
    private BigDecimal subtotal;
    private String observacion;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long versionProducto;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long versionCategoria;

    public static DetallePedidoResponse de(CotizacionDetalle detalle) {
        DetallePedidoResponse dto = new DetallePedidoResponse();
        dto.setProductoId(detalle.getProductoId());
        dto.setCategoriaId(detalle.getCategoriaId());
        dto.setNombreProducto(detalle.getNombreProducto());
        dto.setPrecioUnitario(detalle.getPrecioUnitario());
        dto.setCantidad(detalle.getCantidad());
        dto.setSubtotal(detalle.getSubtotal());
        dto.setObservacion(detalle.getObservacion());
        dto.setVersionProducto(detalle.getVersionProducto());
        dto.setVersionCategoria(detalle.getVersionCategoria());
        return dto;
    }

    public static DetallePedidoResponse de(DetallePedido detalle) {
        DetallePedidoResponse dto = new DetallePedidoResponse();
        dto.setProductoId(detalle.getProductoId());
        dto.setCategoriaId(detalle.getCategoriaId());
        dto.setNombreProducto(detalle.getNombreProducto());
        dto.setPrecioUnitario(detalle.getPrecioUnitario());
        dto.setCantidad(detalle.getCantidad());
        dto.setSubtotal(detalle.getSubtotal());
        dto.setObservacion(detalle.getObservacion());
        return dto;
    }
}
