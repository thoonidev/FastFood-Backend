package com.fastfood.order.service;

import com.fastfood.order.dto.ComprobanteResponse;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.model.*;
import com.fastfood.order.repository.PedidoRepository;
import com.fastfood.order.security.JwtUtil;
import com.fastfood.order.soap.SoapContrato;
import com.fastfood.order.soap.VentaComparacion;
import com.fastfood.order.soap.contract.*;
import io.jsonwebtoken.Claims;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.context.TransportContextHolder;
import javax.xml.transform.dom.DOMSource;
import java.math.BigDecimal;
import java.util.Locale;

@Service
@Slf4j
public class ComprobanteFacade {
    private final PedidoRepository pedidos;
    private final TransactionTemplate transaccion;
    private final WebServiceTemplate soap;
    private final JwtUtil jwt;

    public ComprobanteFacade(PedidoRepository pedidos, PlatformTransactionManager manager,
            WebServiceTemplate comprobantesTemplate, JwtUtil jwt) {
        this.pedidos = pedidos;
        this.transaccion = new TransactionTemplate(manager);
        this.transaccion.setReadOnly(true);
        this.soap = comprobantesTemplate;
        this.jwt = jwt;
    }

    public Emision generar(Long pedidoId, Claims actor) {
        Venta venta = obtenerVenta(pedidoId, actor);
        GenerarComprobanteRequest request = new GenerarComprobanteRequest();
        request.setVenta(venta);
        Object resultado = solicitar(request, "generarComprobante");
        if (!(resultado instanceof GenerarComprobanteResponse response)) throw noDisponible();
        return new Emision(validarRespuesta(response.getComprobante(), venta), response.isCreado());
    }

    public ComprobanteResponse consultar(Long pedidoId, Claims actor) {
        Venta venta = obtenerVenta(pedidoId, actor);
        ConsultarComprobanteRequest request = new ConsultarComprobanteRequest();
        request.setPedidoId(pedidoId);
        Object resultado = solicitar(request, "consultarComprobante");
        if (!(resultado instanceof ConsultarComprobanteResponse response)) throw noDisponible();
        return validarRespuesta(response.getComprobante(), venta);
    }

    // Se copian los datos historicos antes de realizar la llamada remota.
    private Venta obtenerVenta(Long pedidoId, Claims actor) {
        return transaccion.execute(status -> {
            Pedido pedido = pedidos.findById(pedidoId).orElseThrow(() ->
                    new NegocioException(404, "RECURSO_NO_ENCONTRADO", "El pedido no existe."));
            ConversacionService.exigirAcceso(pedido, actor);
            if (pedido.getEstadoPedido() == Pedido.EstadoPedido.CANCELADO
                    || pedido.getPago().getEstadoPago() != Pago.EstadoPago.PAGADO) {
                throw new NegocioException(409, "TRANSICION_INVALIDA", "El comprobante requiere un pedido pagado y no cancelado.");
            }
            Venta venta = new Venta();
            venta.setPedidoId(pedidoId);
            venta.setClienteId(pedido.getClienteId());
            venta.setClienteNombres(pedido.getClienteNombres().trim());
            venta.setClienteCorreo(pedido.getClienteCorreo().trim().toLowerCase(Locale.ROOT));
            venta.setMetodoPago(pedido.getPago().getMetodoPago().name());
            venta.setSubtotal(pedido.getTotal());
            venta.setImpuesto(new BigDecimal("0.00"));
            venta.setTotal(pedido.getTotal());
            venta.getDetalle().addAll(pedido.getDetalles().stream().map(linea -> {
                Detalle detalle = new Detalle();
                detalle.setProductoId(linea.getProductoId());
                detalle.setNombreProducto(linea.getNombreProducto().trim());
                detalle.setCantidad(linea.getCantidad());
                detalle.setPrecioUnitario(linea.getPrecioUnitario());
                detalle.setSubtotal(linea.getSubtotal());
                detalle.setObservacion(linea.getObservacion() == null || linea.getObservacion().isBlank()
                        ? null : linea.getObservacion().trim());
                return detalle;
            }).toList());
            return venta;
        });
    }

    private Object solicitar(Object request, String accion) {
        try {
            return soap.marshalSendAndReceive(request, message -> {
                new SoapActionCallback(SoapContrato.NAMESPACE + ":" + accion).doWithMessage(message);
                HeadersAwareSenderWebServiceConnection conexion = (HeadersAwareSenderWebServiceConnection)
                        TransportContextHolder.getTransportContext().getConnection();
                conexion.addRequestHeader("Authorization", "Bearer " + jwt.generarTecnicoComprobante());
            });
        } catch (SoapFaultClientException ex) {
            if ("Client".equals(ex.getFaultCode().getLocalPart()) && ex.getSoapFault().getFaultDetail() != null) {
                var entries = ex.getSoapFault().getFaultDetail().getDetailEntries();
                while (entries.hasNext()) {
                    var element = entries.next();
                    if (SoapContrato.NAMESPACE.equals(element.getName().getNamespaceURI())
                            && "code".equals(element.getName().getLocalPart())
                            && element.getSource() instanceof DOMSource source) {
                        String code = source.getNode().getTextContent();
                        switch (code) {
                            case "VALIDACION" -> throw new NegocioException(400, code, "Los datos del comprobante no son validos.");
                            case "COMPROBANTE_NO_ENCONTRADO" -> throw new NegocioException(404, code, "El comprobante aun no fue emitido.");
                            case "DATOS_COMPROBANTE_CONFLICTO" -> throw new NegocioException(409, code, "El pedido ya tiene un comprobante con otros datos.");
                            default -> { }
                        }
                    }
                }
            }
            throw noDisponible();
        } catch (RuntimeException ex) {
            log.warn("No se pudo completar la operacion SOAP {}. Revisar disponibilidad y credencial tecnica. Tipo: {}",
                    accion, ex.getClass().getSimpleName());
            throw noDisponible();
        }
    }

    private ComprobanteResponse validarRespuesta(Comprobante comprobante, Venta venta) {
        try {
            if (comprobante == null || comprobante.getId() <= 0
                    || !String.format(Locale.ROOT, "FFA-%06d", venta.getPedidoId()).equals(comprobante.getNumero())
                    || !VentaComparacion.coincide(venta, comprobante.getVenta())) throw noDisponible();
            return ComprobanteResponse.de(comprobante);
        } catch (RuntimeException ex) {
            throw noDisponible();
        }
    }

    private NegocioException noDisponible() {
        return new NegocioException(503, "COMPROBANTE_NO_DISPONIBLE", "El servicio de comprobantes no esta disponible. Puedes volver a intentarlo.");
    }

    @Data
    @AllArgsConstructor
    public static class Emision {
        private ComprobanteResponse comprobante;
        private boolean creado;
    }
}
