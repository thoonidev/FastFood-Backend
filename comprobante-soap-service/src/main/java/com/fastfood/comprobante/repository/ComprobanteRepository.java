package com.fastfood.comprobante.repository;

import com.fastfood.comprobante.model.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {
    Optional<Comprobante> findByPedidoId(Long pedidoId);
}
