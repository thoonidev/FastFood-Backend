package com.fastfood.order.model;

import jakarta.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "cotizaciones")
@Data
public class Cotizacion {

    public enum EstadoCotizacion {
        VIGENTE,
        UTILIZADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant creadaEn;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant expiraEn;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 64)
    private String huellaCatalogo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCotizacion estado = EstadoCotizacion.VIGENTE;

    @Version private Long version;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<CotizacionDetalle> detalles = new ArrayList<>();
}
