package com.fastfood.order.repository;

import com.fastfood.order.model.Conversacion;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long>, JpaSpecificationExecutor<Conversacion> {

    Optional<Conversacion> findByPedidoId(Long pedidoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversacion c where c.pedido.id = :pedidoId")
    Optional<Conversacion> obtenerConBloqueo(@Param("pedidoId") Long pedidoId);
}
