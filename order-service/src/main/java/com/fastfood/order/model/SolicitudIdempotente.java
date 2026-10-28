package com.fastfood.order.model;

import jakarta.persistence.*;

import lombok.Data;

import java.time.Instant;
import java.util.*;

@Entity
@Table(
        name = "solicitudes_idempotentes",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_idempotencia_actor_operacion_clave",
                        columnNames = {"actor_id", "operacion", "clave"}))
@Data
public class SolicitudIdempotente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 80)
    private String operacion;

    @Column(nullable = false)
    private UUID clave;

    @Column(nullable = false, length = 64)
    private String huella;

    @Column(length = 30)
    private String recursoTipo;

    @Column(length = 64)
    private String recursoId;

    private Integer httpStatus;

    @Column(columnDefinition = "datetime(6)")
    private Instant completadaEn;

    @Column(columnDefinition = "longtext")
    private String responseJson;
}
