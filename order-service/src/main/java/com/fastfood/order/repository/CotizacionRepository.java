package com.fastfood.order.repository;

import com.fastfood.order.model.Cotizacion;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface CotizacionRepository extends JpaRepository<Cotizacion, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cotizacion c where c.id = :id")
    Optional<Cotizacion> obtenerConBloqueo(@Param("id") UUID id);
}
