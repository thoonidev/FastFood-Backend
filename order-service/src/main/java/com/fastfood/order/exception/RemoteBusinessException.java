package com.fastfood.order.exception;

import java.util.Map;

public class RemoteBusinessException extends NegocioException {

    public RemoteBusinessException(
            int status, String code, String message, Map<String, Object> details) {
        super(status, code, message, details);
    }
}
