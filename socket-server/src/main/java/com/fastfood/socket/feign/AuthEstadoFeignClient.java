package com.fastfood.socket.feign;

import com.fastfood.socket.dto.UsuarioEstadoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "auth-service")
public interface AuthEstadoFeignClient {
    @GetMapping("/internal/auth/usuarios/{id}/estado")
    UsuarioEstadoResponse obtener(@PathVariable Long id, @RequestHeader("Authorization") String authorization);
}
