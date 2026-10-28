package com.fastfood.order.repository;

import com.fastfood.order.model.MensajeChat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {

    Optional<MensajeChat> findByConversacionIdAndRemitenteIdAndClientMessageId(
            Long conversacionId, Long remitenteId, UUID clientMessageId);

    List<MensajeChat> findByConversacionIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(
            Long conversacionId, Long secuencia, Pageable pageable);
}
