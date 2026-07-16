package com.portfolio.tracker.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    List<Portfolio> findByUserId(UUID userId);

    Boolean existsByNameAndUserId(String name, UUID userId);

    Boolean existsByNameAndUserIdAndIdNot(String name, UUID userId, UUID id);

    @Query("SELECT p FROM Portfolio p WHERE p.id = :portfolioId AND p.user.id = :userId")
    Optional<Portfolio> findByIdAndUserId(@Param("portfolioId") UUID portfolioId, @Param("userId") UUID userId);
}
