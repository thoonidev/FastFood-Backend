package com.fastfood.order.config;

import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.exception.RemoteBusinessException;

import feign.codec.ErrorDecoder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Configuration
public class FeignCircuitBreakerConfig {

    @Bean
    public CircuitBreakerNameResolver circuitBreakerNameResolver() {
        return (nombre, target, metodo) -> nombre;
    }

    @Bean
    public ErrorDecoder catalogErrorDecoder(JsonMapper mapper) {
        return (metodo, response) -> {
            if (response.status() == 400 || response.status() == 404 || response.status() == 409) {
                try {
                    var body = mapper.readTree(response.body().asInputStream());
                    if (response.status() == 409
                            && "PRODUCTO_NO_DISPONIBLE".equals(body.path("code").asString())) {
                        java.util.List<Long> ids = new java.util.ArrayList<>();
                        for (var id : body.path("details").path("productoIds")) {
                            if (id.isIntegralNumber() && id.asLong() > 0 && ids.size() < 40) {
                                ids.add(id.asLong());
                            }
                        }
                        return new RemoteBusinessException(
                                409,
                                "PRODUCTO_NO_DISPONIBLE",
                                "Uno o mas productos no estan disponibles.",
                                Map.of("productoIds", ids));
                    }
                    return new RemoteBusinessException(
                            response.status(),
                            "VALIDACION",
                            "El catalogo rechazo la solicitud.",
                            Map.of());
                } catch (Exception ex) {
                    return new NegocioException(
                            503,
                            "DEPENDENCIA_NO_DISPONIBLE",
                            "La respuesta del catalogo no es valida.");
                }
            }
            return new NegocioException(
                    503, "DEPENDENCIA_NO_DISPONIBLE", "El catalogo no esta disponible.");
        };
    }
}
