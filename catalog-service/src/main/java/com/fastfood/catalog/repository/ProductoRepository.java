package com.fastfood.catalog.repository;

import com.fastfood.catalog.model.Producto;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductoRepository
        extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    @Query("select p from Producto p join fetch p.categoria where p.id in :ids")
    List<Producto> buscarLoteParaValidacion(@Param("ids") Collection<Long> ids);
}
