package com.fastfood.order.service;

import com.fastfood.order.dto.*;
import com.fastfood.order.event.OrderEventPublisher;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.feign.AuthEstadoFeignClient;
import com.fastfood.order.model.*;
import com.fastfood.order.repository.*;
import com.fastfood.order.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class MensajeChatService {

    private final PedidoRepository pedidos;
    private final ConversacionRepository conversaciones;
    private final MensajeChatRepository mensajes;
    private final AuthEstadoFeignClient auth;
    private final JwtUtil jwt;
    private final OrderEventPublisher eventos;
    private final TransactionTemplate transaccion;

    public MensajeChatService(PedidoRepository pedidos, ConversacionRepository conversaciones,
            MensajeChatRepository mensajes, AuthEstadoFeignClient auth, JwtUtil jwt,
            OrderEventPublisher eventos, PlatformTransactionManager manager) {
        this.pedidos = pedidos;
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.auth = auth;
        this.jwt = jwt;
        this.eventos = eventos;
        transaccion = new TransactionTemplate(manager);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public MensajeChatResponse enviar(Long pedidoId, ChatEnviarRequest request, Claims actor) {
        Long actorId = ((Number) actor.get("usuarioId")).longValue();
        String contenido = request.getContenido().trim();
        UsuarioEstadoResponse usuario = auth.obtener(actorId, "Bearer " + jwt.generarTecnicoAuth());
        if (usuario == null || usuario.getNombres() == null || usuario.getApellidos() == null
                || usuario.getVersionSeguridad() == null || usuario.getRol() == null || usuario.getCorreo() == null) {
            throw new NegocioException(503, "DEPENDENCIA_NO_DISPONIBLE", "La respuesta de Auth no es valida.");
        }
        if (!actorId.equals(usuario.getId()) || !"ACTIVO".equals(usuario.getEstado())
                || !usuario.getVersionSeguridad().equals(((Number) actor.get("versionSeguridad")).longValue())
                || !usuario.getRol().equals(actor.get("rol")) || !usuario.getCorreo().equals(actor.get("correo"))) {
            throw new NegocioException(401, "SESION_VENCIDA", "La sesion ya no es valida.");
        }
        boolean[] nuevo = {false};
        MensajeChatResponse respuesta = transaccion.execute(status -> {
            Pedido pedido = pedidos.obtenerConBloqueo(pedidoId).orElseThrow(() ->
                    new NegocioException(404, "RECURSO_NO_ENCONTRADO", "El pedido no existe."));
            ConversacionService.exigirAcceso(pedido, actor);
            Conversacion conversacion = conversaciones.obtenerConBloqueo(pedidoId).orElseThrow();
            var anterior = mensajes.findByConversacionIdAndRemitenteIdAndClientMessageId(
                    conversacion.getId(), actorId, request.getClientMessageId());
            if (anterior.isPresent()) {
                if (!anterior.get().getContenido().equals(contenido)) {
                    throw new NegocioException(409, "IDEMPOTENCIA_CONFLICTO", "El identificador ya fue usado con otro contenido.");
                }
                return MensajeChatResponse.de(anterior.get());
            }
            if (!ConversacionResponse.de(conversacion).isPuedeEnviar()) {
                throw new NegocioException(409, "CHAT_CERRADO", "La conversacion esta cerrada.");
            }
            long secuencia = conversacion.getUltimaSecuencia() + 1;
            MensajeChat mensaje = new MensajeChat();
            mensaje.setConversacion(conversacion);
            mensaje.setSecuencia(secuencia);
            mensaje.setRemitenteId(actorId);
            mensaje.setRemitenteNombre(usuario.getNombres() + " " + usuario.getApellidos());
            mensaje.setRolRemitente(usuario.getRol());
            mensaje.setContenido(contenido);
            mensaje.setFechaHora(Instant.now().truncatedTo(ChronoUnit.MICROS));
            mensaje.setClientMessageId(request.getClientMessageId());
            conversacion.setUltimaSecuencia(secuencia);
            if ("CLIENTE".equals(usuario.getRol())) {
                conversacion.setUltimaSecuenciaCliente(secuencia);
            } else {
                conversacion.setUltimaSecuenciaPersonal(secuencia);
            }
            mensajes.saveAndFlush(mensaje);
            nuevo[0] = true;
            return MensajeChatResponse.de(mensaje);
        });
        if (nuevo[0]) {
            try {
                eventos.publicarMensaje(pedidoId, respuesta);
            } catch (RuntimeException ex) {
                log.warn("Mensaje persistido sin aviso inmediato: pedidoId={}, mensajeId={}", pedidoId, respuesta.getId());
            }
        }
        return respuesta;
    }

    @Transactional(readOnly = true)
    public MensajeHistorialResponse historial(Long pedidoId, long afterSecuencia, int limit, Claims actor) {
        if (afterSecuencia < 0 || limit < 1 || limit > 100) {
            throw new NegocioException(400, "VALIDACION", "El cursor o limite no es valido.");
        }
        Pedido pedido = pedidos.findById(pedidoId).orElseThrow(() ->
                new NegocioException(404, "RECURSO_NO_ENCONTRADO", "El pedido no existe."));
        ConversacionService.exigirAcceso(pedido, actor);
        Conversacion conversacion = conversaciones.findByPedidoId(pedidoId).orElseThrow();
        var pagina = mensajes.findByConversacionIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(
                conversacion.getId(), afterSecuencia, PageRequest.of(0, limit + 1));
        boolean hayMas = pagina.size() > limit;
        var content = pagina.stream().limit(limit).map(MensajeChatResponse::de).toList();
        long ultima = content.isEmpty() ? afterSecuencia : content.get(content.size() - 1).getSecuencia();
        return new MensajeHistorialResponse(content, ultima, conversacion.getUltimaSecuencia(), hayMas);
    }
}
