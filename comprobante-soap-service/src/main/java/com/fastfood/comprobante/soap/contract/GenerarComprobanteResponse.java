
package com.fastfood.comprobante.soap.contract;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
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
 *         &lt;element name="comprobante" type="{urn:fastfood:comprobantes:v1}Comprobante"/&gt;
 *         &lt;element name="creado" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
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
    "comprobante",
    "creado"
})
@XmlRootElement(name = "generarComprobanteResponse")
public class GenerarComprobanteResponse {

    @XmlElement(required = true)
    protected Comprobante comprobante;
    protected boolean creado;

    /**
     * Obtiene el valor de la propiedad comprobante.
     * 
     * @return
     *     possible object is
     *     {@link Comprobante }
     *     
     */
    public Comprobante getComprobante() {
        return comprobante;
    }

    /**
     * Define el valor de la propiedad comprobante.
     * 
     * @param value
     *     allowed object is
     *     {@link Comprobante }
     *     
     */
    public void setComprobante(Comprobante value) {
        this.comprobante = value;
    }

    /**
     * Obtiene el valor de la propiedad creado.
     * 
     */
    public boolean isCreado() {
        return creado;
    }

    /**
     * Define el valor de la propiedad creado.
     * 
     */
    public void setCreado(boolean value) {
        this.creado = value;
    }

}
