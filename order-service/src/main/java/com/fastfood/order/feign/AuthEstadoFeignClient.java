package com.fastfood.order.feign;

import com.fastfood.order.config.AuthFeignSinCircuitBreakerConfig;
import com.fastfood.order.dto.UsuarioEstadoResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "auth-service", configuration = AuthFeignSinCircuitBreakerConfig.class)
public interface AuthEstadoFeignClient {

    @GetMapping("/internal/auth/usuarios/{id}/estado")
    UsuarioEstadoResponse obtener(
            @PathVariable("id") Long id, @RequestHeader("Authorization") String authorization);
}
