package com.fastfood.order.repository;

import com.fastfood.order.model.PedidoCambioEstado;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface PedidoCambioEstadoRepository extends JpaRepository<PedidoCambioEstado, Long> {

    Page<PedidoCambioEstado> findByPedidoId(Long pedidoId, Pageable pageable);
}
