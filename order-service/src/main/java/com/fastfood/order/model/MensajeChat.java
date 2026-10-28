package com.fastfood.order.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensajes_chat", uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_secuencia", columnNames = {"conversacion_id", "secuencia"}),
        @UniqueConstraint(name = "uk_chat_client_message", columnNames = {"conversacion_id", "remitente_id", "client_message_id"})})
@Data
public class MensajeChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversacion_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversacion conversacion;

    @Column(nullable = false)
    private Long secuencia;

    @Column(nullable = false)
    private Long remitenteId;

    @Column(nullable = false, length = 201)
    private String remitenteNombre;

    @Column(nullable = false, length = 20)
    private String rolRemitente;

    @Column(nullable = false, length = 1000)
    private String contenido;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private Instant fechaHora;

    @Column(nullable = false, columnDefinition = "binary(16)")
    private UUID clientMessageId;
}
