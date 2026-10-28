package com.fastfood.comprobante.soap;

import com.fastfood.comprobante.service.ComprobanteService;
import com.fastfood.comprobante.soap.SoapContrato;
import com.fastfood.comprobante.soap.contract.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.*;

@Endpoint
@RequiredArgsConstructor
public class ComprobanteEndpoint {
    private final ComprobanteService comprobantes;

    @PayloadRoot(namespace = SoapContrato.NAMESPACE, localPart = "generarComprobanteRequest")
    @ResponsePayload
    public GenerarComprobanteResponse generar(@RequestPayload GenerarComprobanteRequest request) {
        return comprobantes.generar(request.getVenta());
    }

    @PayloadRoot(namespace = SoapContrato.NAMESPACE, localPart = "consultarComprobanteRequest")
    @ResponsePayload
    public ConsultarComprobanteResponse consultar(@RequestPayload ConsultarComprobanteRequest request) {
        return comprobantes.consultar(request.getPedidoId());
    }
}
