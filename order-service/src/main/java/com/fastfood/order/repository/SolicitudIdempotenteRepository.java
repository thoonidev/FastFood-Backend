package com.fastfood.order.repository;

import com.fastfood.order.model.SolicitudIdempotente;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface SolicitudIdempotenteRepository extends JpaRepository<SolicitudIdempotente, UUID> {

    Optional<SolicitudIdempotente> findByActorIdAndOperacionAndClave(
            Long actorId, String operacion, UUID clave);
}
