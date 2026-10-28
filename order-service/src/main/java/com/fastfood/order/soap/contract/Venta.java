
package com.fastfood.order.soap.contract;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Clase Java para Venta complex type.
 * 
 * <p>El siguiente fragmento de esquema especifica el contenido que se espera que haya en esta clase.
 * 
 * <pre>
 * &lt;complexType name="Venta"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="pedidoId" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="clienteId" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="clienteNombres" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="clienteCorreo" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="metodoPago" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="subtotal" type="{urn:fastfood:comprobantes:v1}Importe"/&gt;
 *         &lt;element name="impuesto" type="{urn:fastfood:comprobantes:v1}Importe"/&gt;
 *         &lt;element name="total" type="{urn:fastfood:comprobantes:v1}Importe"/&gt;
 *         &lt;element name="detalle" type="{urn:fastfood:comprobantes:v1}Detalle" maxOccurs="40"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Venta", propOrder = {
    "pedidoId",
    "clienteId",
    "clienteNombres",
    "clienteCorreo",
    "metodoPago",
    "subtotal",
    "impuesto",
    "total",
    "detalle"
})
public class Venta {

    protected long pedidoId;
    protected long clienteId;
    @XmlElement(required = true)
    protected String clienteNombres;
    @XmlElement(required = true)
    protected String clienteCorreo;
    @XmlElement(required = true)
    protected String metodoPago;
    @XmlElement(required = true)
    protected BigDecimal subtotal;
    @XmlElement(required = true)
    protected BigDecimal impuesto;
    @XmlElement(required = true)
    protected BigDecimal total;
    @XmlElement(required = true)
    protected List<Detalle> detalle;

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

    /**
     * Obtiene el valor de la propiedad clienteId.
     * 
     */
    public long getClienteId() {
        return clienteId;
    }

    /**
     * Define el valor de la propiedad clienteId.
     * 
     */
    public void setClienteId(long value) {
        this.clienteId = value;
    }

    /**
     * Obtiene el valor de la propiedad clienteNombres.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getClienteNombres() {
        return clienteNombres;
    }

    /**
     * Define el valor de la propiedad clienteNombres.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setClienteNombres(String value) {
        this.clienteNombres = value;
    }

    /**
     * Obtiene el valor de la propiedad clienteCorreo.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getClienteCorreo() {
        return clienteCorreo;
    }

    /**
     * Define el valor de la propiedad clienteCorreo.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setClienteCorreo(String value) {
        this.clienteCorreo = value;
    }

    /**
     * Obtiene el valor de la propiedad metodoPago.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMetodoPago() {
        return metodoPago;
    }

    /**
     * Define el valor de la propiedad metodoPago.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMetodoPago(String value) {
        this.metodoPago = value;
    }

    /**
     * Obtiene el valor de la propiedad subtotal.
     * 
     * @return
     *     possible object is
     *     {@link BigDecimal }
     *     
     */
    public BigDecimal getSubtotal() {
        return subtotal;
    }

    /**
     * Define el valor de la propiedad subtotal.
     * 
     * @param value
     *     allowed object is
     *     {@link BigDecimal }
     *     
     */
    public void setSubtotal(BigDecimal value) {
        this.subtotal = value;
    }

    /**
     * Obtiene el valor de la propiedad impuesto.
     * 
     * @return
     *     possible object is
     *     {@link BigDecimal }
     *     
     */
    public BigDecimal getImpuesto() {
        return impuesto;
    }

    /**
     * Define el valor de la propiedad impuesto.
     * 
     * @param value
     *     allowed object is
     *     {@link BigDecimal }
     *     
     */
    public void setImpuesto(BigDecimal value) {
        this.impuesto = value;
    }

    /**
     * Obtiene el valor de la propiedad total.
     * 
     * @return
     *     possible object is
     *     {@link BigDecimal }
     *     
     */
    public BigDecimal getTotal() {
        return total;
    }

    /**
     * Define el valor de la propiedad total.
     * 
     * @param value
     *     allowed object is
     *     {@link BigDecimal }
     *     
     */
    public void setTotal(BigDecimal value) {
        this.total = value;
    }

    /**
     * Gets the value of the detalle property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the Jakarta XML Binding object.
     * This is why there is not a <CODE>set</CODE> method for the detalle property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDetalle().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Detalle }
     * 
     * 
     */
    public List<Detalle> getDetalle() {
        if (detalle == null) {
            detalle = new ArrayList<Detalle>();
        }
        return this.detalle;
    }

}
