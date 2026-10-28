package com.fastfood.order.config;

import feign.Feign;
import feign.codec.ErrorDecoder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

// Auth y Socket quedan fuera del Circuit Breaker de Catalogo.
public class AuthFeignSinCircuitBreakerConfig {

    // Auth conserva sus errores HTTP; el decoder de Catalogo no se aplica aqui.
    @Bean
    public ErrorDecoder authErrorDecoder() {
        return new ErrorDecoder.Default();
    }

    @Bean
    @Scope("prototype")
    public Feign.Builder authFeignBuilder() {
        return Feign.builder();
    }
}
