package com.portfolio.tracker.notification.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserId(UUID userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.endpoint = :endpoint AND s.userId = :userId")
    int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") UUID userId);
}
