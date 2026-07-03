package com.portfolio.tracker.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    List<Portfolio> findByUserId(UUID userId);
    Boolean existsByNameAndUserId(String name, UUID userId);
    Boolean existsByNameAndUserIdAndIdNot(String name, UUID userId, UUID id);
}
