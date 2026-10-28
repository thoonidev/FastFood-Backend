package com.fastfood.catalog.feign;

import com.fastfood.catalog.dto.UsuarioEstadoResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "auth-service")
public interface AuthEstadoFeignClient {

    @GetMapping("/internal/auth/usuarios/{id}/estado")
    UsuarioEstadoResponse obtener(
            @PathVariable("id") Long id, @RequestHeader("Authorization") String authorization);
}
