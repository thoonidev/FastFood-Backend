
package com.fastfood.order.soap.contract;

import jakarta.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.fastfood.order.soap.contract package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.fastfood.order.soap.contract
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link GenerarComprobanteRequest }
     * 
     */
    public GenerarComprobanteRequest createGenerarComprobanteRequest() {
        return new GenerarComprobanteRequest();
    }

    /**
     * Create an instance of {@link Venta }
     * 
     */
    public Venta createVenta() {
        return new Venta();
    }

    /**
     * Create an instance of {@link GenerarComprobanteResponse }
     * 
     */
    public GenerarComprobanteResponse createGenerarComprobanteResponse() {
        return new GenerarComprobanteResponse();
    }

    /**
     * Create an instance of {@link Comprobante }
     * 
     */
    public Comprobante createComprobante() {
        return new Comprobante();
    }

    /**
     * Create an instance of {@link ConsultarComprobanteRequest }
     * 
     */
    public ConsultarComprobanteRequest createConsultarComprobanteRequest() {
        return new ConsultarComprobanteRequest();
    }

    /**
     * Create an instance of {@link ConsultarComprobanteResponse }
     * 
     */
    public ConsultarComprobanteResponse createConsultarComprobanteResponse() {
        return new ConsultarComprobanteResponse();
    }

    /**
     * Create an instance of {@link Detalle }
     * 
     */
    public Detalle createDetalle() {
        return new Detalle();
    }

}
