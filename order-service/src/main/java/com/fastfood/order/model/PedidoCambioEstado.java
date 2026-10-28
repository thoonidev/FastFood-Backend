package com.fastfood.order.model;

import jakarta.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pedido_cambios_estado")
@Data
public class PedidoCambioEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Pedido.EstadoPedido anterior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Pedido.EstadoPedido nuevo;

    @Column(nullable = false)
    private Long actorId;

    @Column(length = 250)
    private String motivo;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant fecha;
}
