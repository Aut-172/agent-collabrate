package com.example.agentcollab.repository;

import com.example.agentcollab.domain.WebhookEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from WebhookEvent e where e.provider = :provider and e.deliveryId = :deliveryId")
    Optional<WebhookEvent> findByProviderAndDeliveryIdForUpdate(@Param("provider") String provider,
                                                                  @Param("deliveryId") String deliveryId);
}
