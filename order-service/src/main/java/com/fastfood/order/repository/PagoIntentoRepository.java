package com.fastfood.order.repository;

import com.fastfood.order.model.PagoIntento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoIntentoRepository extends JpaRepository<PagoIntento, Long> {

    Page<PagoIntento> findByPagoId(Long pagoId, Pageable pageable);

    long countByPagoId(Long pagoId);
}
