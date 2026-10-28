package com.fastfood.order.model;

import jakarta.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pagos")
@Data
public class Pago {

    public enum MetodoPago {
        EFECTIVO,
        YAPE,
        TARJETA
    }

    public enum EstadoPago {
        PENDIENTE,
        PENDIENTE_VALIDACION,
        PAGADO,
        RECHAZADO,
        ANULADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPago metodoPago;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    @Column(precision = 12, scale = 2)
    private BigDecimal montoAnunciado;

    @Column(precision = 12, scale = 2)
    private BigDecimal montoRecibido;

    @Column(precision = 12, scale = 2)
    private BigDecimal vueltoEstimado;

    @Column(precision = 12, scale = 2)
    private BigDecimal vueltoFinal;

    @Column(columnDefinition = "datetime(6)")
    private Instant fechaPago;

    @Column(length = 40)
    private String referenciaOperacion;

    private Long intentoActualId;

    @Version private Long version;
}
