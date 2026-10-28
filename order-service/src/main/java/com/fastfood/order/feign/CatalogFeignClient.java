package com.fastfood.order.feign;

import com.fastfood.order.dto.*;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "catalog-service",
        fallbackFactory = CatalogFeignFallbackFactory.class)
public interface CatalogFeignClient {

    @PostMapping("/internal/catalogo/validar")
    CatalogoValidacionResponse validar(
            @RequestBody CatalogoValidacionRequest request,
            @RequestHeader("Authorization") String authorization);
}
