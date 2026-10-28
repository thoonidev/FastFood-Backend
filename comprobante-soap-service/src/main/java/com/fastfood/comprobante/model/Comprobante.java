package com.fastfood.comprobante.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "comprobantes")
@Data
public class Comprobante {
    public enum MetodoPago {
        EFECTIVO,
        YAPE,
        TARJETA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long pedidoId;

    @Column(nullable = false, unique = true, length = 30)
    private String numero;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant fechaEmision;

    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false, length = 201)
    private String clienteNombres;

    @Column(nullable = false, length = 150)
    private String clienteCorreo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MetodoPago metodoPago;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal impuesto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 64)
    private String huellaContenido;

    @OneToMany(mappedBy = "comprobante", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ComprobanteDetalle> detalles = new ArrayList<>();
}
