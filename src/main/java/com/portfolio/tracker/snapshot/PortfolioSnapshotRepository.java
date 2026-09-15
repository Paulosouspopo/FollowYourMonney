package com.portfolio.tracker.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

        @Query("""
                        SELECT s FROM PortfolioSnapshot s
                        WHERE s.portfolio.user.id = :userId
                          AND s.snapshotDate >= :from
                        ORDER BY s.snapshotDate ASC
                        """)
        List<PortfolioSnapshot> findByUserIdSince(@Param("userId") UUID userId,
                        @Param("from") LocalDate from);

        Optional<PortfolioSnapshot> findByPortfolioIdAndSnapshotDate(UUID portfolioId, LocalDate snapshotDate);

        @Query("""
                        SELECT s FROM PortfolioSnapshot s
                        WHERE s.portfolio.id = :portfolioId
                          AND s.snapshotDate >= :from
                        ORDER BY s.snapshotDate ASC
                        """)
        List<PortfolioSnapshot> findByPortfolioSince(@Param("portfolioId") UUID portfolioId,
                        @Param("from") LocalDate from);

        List<PortfolioSnapshot> findByPortfolioIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                        UUID portfolioId, LocalDate startDate);

        List<PortfolioSnapshot> findByPortfolioIdOrderBySnapshotDateAsc(UUID portfolioId);

        @Query("""
                        SELECT s FROM PortfolioSnapshot s
                        WHERE s.portfolio.user.id = :userId
                          AND s.snapshotDate >= :startDate
                        ORDER BY s.snapshotDate ASC
                        """)
        List<PortfolioSnapshot> findByUserIdAndSnapshotDateGreaterThanEqual(
                        @Param("userId") UUID userId, @Param("startDate") LocalDate startDate);

        @Query("""
                        SELECT s FROM PortfolioSnapshot s
                        WHERE s.portfolio.user.id = :userId
                        ORDER BY s.snapshotDate ASC
                        """)
        List<PortfolioSnapshot> findAllByUserIdOrderBySnapshotDateAsc(@Param("userId") UUID userId);
}