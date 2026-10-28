
package com.fastfood.comprobante.soap.contract;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Clase Java para anonymous complex type.
 * 
 * <p>El siguiente fragmento de esquema especifica el contenido que se espera que haya en esta clase.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="pedidoId" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "pedidoId"
})
@XmlRootElement(name = "consultarComprobanteRequest")
public class ConsultarComprobanteRequest {

    protected long pedidoId;

    /**
     * Obtiene el valor de la propiedad pedidoId.
     * 
     */
    public long getPedidoId() {
        return pedidoId;
    }

    /**
     * Define el valor de la propiedad pedidoId.
     * 
     */
    public void setPedidoId(long value) {
        this.pedidoId = value;
    }

}
