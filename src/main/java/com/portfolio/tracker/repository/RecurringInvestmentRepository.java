package com.portfolio.tracker.repository;

import com.portfolio.tracker.entity.RecurringInvestment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringInvestmentRepository extends JpaRepository<RecurringInvestment, UUID> {
    List<RecurringInvestment> findByAssetId(UUID assetId);
    List<RecurringInvestment> findByActiveTrue();
    List<RecurringInvestment> findByActiveTrueAndNextExecutionBefore(LocalDateTime date);
    List<RecurringInvestment> findByActiveAndNextExecutionBefore(Boolean active, LocalDateTime dateTime);
}
