package com.fastfood.catalog.model;

import jakarta.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "productos")
@Data
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Categoria categoria;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false, length = 2048)
    private String imagenUrl;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(nullable = false)
    private boolean destacado = false;

    @Version private Long version;

    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant fechaCreacion;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant fechaActualizacion;

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
