package com.fastfood.catalog.model;

import jakarta.persistence.*;

import lombok.Data;

import java.time.Instant;

@Entity
@Table(
        name = "categorias",
        uniqueConstraints = @UniqueConstraint(name = "uk_categoria_nombre", columnNames = "nombre"))
@Data
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;

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
