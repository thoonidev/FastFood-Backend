package com.fastfood.order.service;

import com.fastfood.order.dto.*;
import com.fastfood.order.exception.NegocioException;
import com.fastfood.order.model.*;
import com.fastfood.order.repository.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConversacionService {

    private final PedidoRepository pedidos;
    private final ConversacionRepository conversaciones;

    @Transactional(readOnly = true)
    public ConversacionResponse consultarAcceso(Long pedidoId, Claims actor) {
        Pedido pedido = pedidos.findById(pedidoId).orElseThrow(() ->
                new NegocioException(404, "RECURSO_NO_ENCONTRADO", "El pedido no existe."));
        exigirAcceso(pedido, actor);
        return ConversacionResponse.de(conversaciones.findByPedidoId(pedidoId).orElseThrow(() ->
                new NegocioException(404, "RECURSO_NO_ENCONTRADO", "La conversacion no existe.")));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ConversacionResponse> listar(Boolean pendienteRespuesta,
            Conversacion.EstadoConversacion estado, int page, int size, Claims actor) {
        exigirPersonal(actor);
        if (page < 0 || size < 1 || size > 100) {
            throw new NegocioException(400, "VALIDACION", "La paginacion no es valida.");
        }
        Specification<Conversacion> filtro = (root, query, cb) -> cb.conjunction();
        if (estado != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (pendienteRespuesta != null) {
            filtro = filtro.and((root, query, cb) -> pendienteRespuesta
                    ? cb.greaterThan(root.get("ultimaSecuenciaCliente"), root.get("ultimaSecuenciaPersonal"))
                    : cb.lessThanOrEqualTo(root.get("ultimaSecuenciaCliente"), root.get("ultimaSecuenciaPersonal")));
        }
        return PaginaResponse.de(conversaciones.findAll(filtro, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("id"))))
                .map(ConversacionResponse::de));
    }

    public static void exigirAcceso(Pedido pedido, Claims actor) {
        if ("CLIENTE".equals(actor.get("rol", String.class))) {
            if (!pedido.getClienteId().equals(((Number) actor.get("usuarioId")).longValue())) {
                throw new NegocioException(403, "ACCESO_DENEGADO", "El pedido pertenece a otro cliente.");
            }
        } else {
            exigirPersonal(actor);
        }
    }

    private static void exigirPersonal(Claims actor) {
        if (!Set.of("EMPLEADO", "ADMINISTRADOR").contains(actor.get("rol", String.class))) {
            throw new NegocioException(403, "ACCESO_DENEGADO", "No tienes permiso para esta operacion.");
        }
    }
}
