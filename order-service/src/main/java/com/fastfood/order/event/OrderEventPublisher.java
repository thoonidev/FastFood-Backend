package com.fastfood.order.event;

import com.fastfood.order.dto.*;
import com.fastfood.order.feign.SocketEventFeignClient;
import com.fastfood.order.repository.PedidoRepository;
import com.fastfood.order.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final PedidoRepository pedidos;
    private final PlatformTransactionManager manager;
    private final SocketEventFeignClient socket;
    private final JwtUtil jwt;

    public void publicar(String tipo, Long pedidoId) {
        enviar(tipo, pedidoId, null);
    }

    public void publicarMensaje(Long pedidoId, MensajeChatResponse mensaje) {
        enviar("MENSAJE_NUEVO", pedidoId, mensaje);
    }

    private void enviar(String tipo, Long pedidoId, MensajeChatResponse mensaje) {
        UUID eventId = UUID.randomUUID();
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("El evento debe publicarse despues del commit.");
            }
            SocketEventRequest evento = new TransactionTemplate(manager).execute(status -> {
                var pedido = pedidos.findById(pedidoId).orElseThrow();
                SocketEventRequest request = new SocketEventRequest();
                request.setEventId(eventId);
                request.setTipo(tipo);
                request.setGeneradoEn(Instant.now());
                request.setIdPedido(pedidoId);
                request.setClienteId(pedido.getClienteId());
                if (mensaje != null) {
                    request.setPayload(Map.of("id", mensaje.getId(), "conversacionId", mensaje.getConversacionId(),
                            "secuencia", mensaje.getSecuencia(), "clientMessageId", mensaje.getClientMessageId(),
                            "remitenteId", mensaje.getRemitenteId(), "remitenteNombre", mensaje.getRemitenteNombre(),
                            "rolRemitente", mensaje.getRolRemitente(), "contenido", mensaje.getContenido(),
                            "fechaHora", mensaje.getFechaHora()));
                } else if ("CHAT_CERRADO".equals(tipo)) {
                    request.setPayload(Map.of("conversacion", ConversacionResponse.de(pedido.getConversacion())));
                } else {
                    request.setPayload(Map.of("pedido", PedidoResumenResponse.de(pedido),
                            "versionPedido", pedido.getVersion(), "versionPago", pedido.getPago().getVersion()));
                }
                return request;
            });
            socket.publicar(evento, "Bearer " + jwt.generarTecnicoSocket());
        } catch (RuntimeException ex) {
            log.warn("Evento no distribuido: eventId={}, pedidoId={}, causa={}",
                    eventId, pedidoId, ex.getClass().getSimpleName());
        }
    }
}
