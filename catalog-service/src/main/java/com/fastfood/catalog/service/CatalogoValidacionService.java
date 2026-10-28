package com.fastfood.catalog.service;

import com.fastfood.catalog.dto.*;
import com.fastfood.catalog.exception.NegocioException;
import com.fastfood.catalog.model.Producto;
import com.fastfood.catalog.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogoValidacionService {

    private final ProductoRepository productos;

    @Transactional(readOnly = true)
    public CatalogoValidacionResponse validar(CatalogoValidarRequest request) {
        List<Long> ids = request.getProductoIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new NegocioException(
                    400, "VALIDACION", "Los IDs de productos no deben repetirse.");
        }
        Map<Long, Producto> encontrados =
                productos.buscarLoteParaValidacion(ids).stream()
                        .collect(Collectors.toMap(Producto::getId, Function.identity()));
        List<Long> noDisponibles =
                ids.stream()
                        .filter(
                                id -> {
                                    Producto producto = encontrados.get(id);
                                    return producto == null
                                            || !producto.isActivo()
                                            || !producto.isDisponible()
                                            || !producto.getCategoria().isActivo();
                                })
                        .toList();
        if (!noDisponibles.isEmpty()) {
            throw new NegocioException(
                    409,
                    "PRODUCTO_NO_DISPONIBLE",
                    "Uno o mas productos no estan disponibles.",
                    Map.of("productoIds", noDisponibles));
        }
        List<ProductoSnapshot> snapshots =
                ids.stream().map(id -> ProductoSnapshot.de(encontrados.get(id))).toList();
        return new CatalogoValidacionResponse(snapshots);
    }
}
