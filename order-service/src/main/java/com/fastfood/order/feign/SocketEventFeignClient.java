package com.fastfood.order.feign;

import com.fastfood.order.config.AuthFeignSinCircuitBreakerConfig;
import com.fastfood.order.dto.SocketEventRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "socket-server", configuration = AuthFeignSinCircuitBreakerConfig.class)
public interface SocketEventFeignClient {
    @PostMapping("/internal/socket/eventos")
    void publicar(@RequestBody SocketEventRequest evento, @RequestHeader("Authorization") String authorization);
}
