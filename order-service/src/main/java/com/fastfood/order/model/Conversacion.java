package com.fastfood.order.model;

import jakarta.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "conversaciones")
@Data
public class Conversacion {

    public enum EstadoConversacion {
        ABIERTA,
        CERRADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pedido pedido;

    @Column(nullable = false)
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoConversacion estado = EstadoConversacion.ABIERTA;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant fechaInicio;

    @Column(columnDefinition = "datetime(6)")
    private Instant fechaCierre;

    @Column(nullable = false)
    private Long ultimaSecuencia = 0L;

    @Column(nullable = false)
    private Long ultimaSecuenciaCliente = 0L;

    @Column(nullable = false)
    private Long ultimaSecuenciaPersonal = 0L;
}
