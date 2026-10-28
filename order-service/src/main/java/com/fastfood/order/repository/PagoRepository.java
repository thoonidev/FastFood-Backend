package com.fastfood.order.repository;

import com.fastfood.order.model.Pago;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.pedido.id = :pedidoId")
    Optional<Pago> obtenerConBloqueo(@Param("pedidoId") Long pedidoId);
}
