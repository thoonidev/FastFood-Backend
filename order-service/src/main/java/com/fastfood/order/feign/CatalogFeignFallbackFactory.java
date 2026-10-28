package com.fastfood.order.feign;

import com.fastfood.order.exception.*;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class CatalogFeignFallbackFactory implements FallbackFactory<CatalogFeignClient> {

    @Override
    public CatalogFeignClient create(Throwable cause) {
        return (request, authorization) -> {
            for (Throwable actual = cause; actual != null; actual = actual.getCause()) {
                if (actual instanceof RemoteBusinessException negocio) {
                    throw negocio;
                }
            }
            throw new NegocioException(
                    503, "DEPENDENCIA_NO_DISPONIBLE", "El catalogo no esta disponible.");
        };
    }
}
