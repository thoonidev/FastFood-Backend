package com.fastfood.socket.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "order-service")
public interface OrderChatFeignClient {
    @PostMapping("/internal/chat/pedidos/{id}/acceso")
    Map<String, Object> acceso(@PathVariable Long id, @RequestHeader("Authorization") String authorization);

    @PostMapping("/internal/chat/pedidos/{id}/mensajes")
    Map<String, Object> enviar(@PathVariable Long id, @RequestBody Map<String, Object> request,
            @RequestHeader("Authorization") String authorization);
}
