package com.fastfood.order.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.Instant;

@Entity
@Table(name = "pago_intentos", uniqueConstraints =
        @UniqueConstraint(name = "uk_pago_numero_intento", columnNames = {"pago_id", "numero_intento"}))
@Data
public class PagoIntento {

    public enum EstadoIntento {
        PENDIENTE_VALIDACION, PAGADO, RECHAZADO, ANULADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pago pago;

    @Column(nullable = false)
    private Integer numeroIntento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoIntento estado;

    @Column(nullable = false, length = 40)
    private String referenciaOperacion;

    @Column(length = 250)
    private String motivoRechazo;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant creadoEn;

    @Column(columnDefinition = "datetime(6)")
    private Instant resueltoEn;

    private Long resueltoPorId;
}
