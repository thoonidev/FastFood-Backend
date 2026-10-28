package com.fastfood.order.model;

import jakarta.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "pedidos")
@Data
public class Pedido {

    public enum EstadoPedido {
        REGISTRADO,
        CONFIRMADO,
        EN_PREPARACION,
        LISTO,
        ENTREGADO,
        CANCELADO
    }

    public enum TipoEntrega {
        RECOJO_LOCAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false, length = 201)
    private String clienteNombres;

    @Column(nullable = false, length = 150)
    private String clienteCorreo;

    @Column(nullable = false, length = 20)
    private String clienteTelefono;

    @Column(nullable = false, unique = true)
    private UUID cotizacionId;

    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant fechaCreacion;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant fechaActualizacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estadoPedido = EstadoPedido.REGISTRADO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEntrega tipoEntrega = TipoEntrega.RECOJO_LOCAL;

    @Column(length = 250)
    private String motivoCancelacion;

    @Version private Long version;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DetallePedido> detalles = new ArrayList<>();

    @OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Pago pago;

    @OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversacion conversacion;

    @PrePersist
    void crearFechas() {
        fechaCreacion = Instant.now();
        fechaActualizacion = fechaCreacion;
    }

    @PreUpdate
    void actualizarFecha() {
        fechaActualizacion = Instant.now();
    }
}
