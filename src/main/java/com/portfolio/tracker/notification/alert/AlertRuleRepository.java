package com.portfolio.tracker.notification.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    @Query("SELECT r FROM AlertRule r LEFT JOIN FETCH r.portfolio WHERE r.userId = :userId ORDER BY r.createdAt ASC")
    List<AlertRule> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT r FROM AlertRule r LEFT JOIN FETCH r.portfolio WHERE r.id = :id AND r.userId = :userId")
    Optional<AlertRule> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT r FROM AlertRule r LEFT JOIN FETCH r.portfolio WHERE r.enabled = true ORDER BY r.userId")
    List<AlertRule> findAllEnabled();

    @Query("SELECT DISTINCT r.symbol FROM AlertRule r WHERE r.enabled = true AND r.symbol IS NOT NULL")
    List<String> findDistinctAssetSymbols();
}
