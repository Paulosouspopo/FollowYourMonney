package com.portfolio.tracker.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

  // ----------------------------------------------------------- courbe (lecture)

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

  @Query("SELECT MAX(s.snapshotDate) FROM PortfolioSnapshot s WHERE s.portfolio.id = :portfolioId")
  Optional<LocalDate> findLastSnapshotDate(@Param("portfolioId") UUID portfolioId);

  /** Dernier snapshot au jour {@code date} inclus : base d'une variation sur une période. */
  Optional<PortfolioSnapshot> findTopByPortfolioIdAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(
      UUID portfolioId, LocalDate date);

  // ---------------------------------------------------------- reconstruction

  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM PortfolioSnapshot s WHERE s.portfolio.id = :portfolioId")
  int deleteByPortfolioId(@Param("portfolioId") UUID portfolioId);

  /**
   * Supprime la plage à recalculer [from, +∞[ ainsi que tout snapshot antérieur
   * à la première transaction (cas d'une transaction déplacée ou supprimée).
   */
  @Modifying(flushAutomatically = true)
  @Query("""
      DELETE FROM PortfolioSnapshot s
      WHERE s.portfolio.id = :portfolioId
        AND (s.snapshotDate < :firstDay OR s.snapshotDate >= :from)
      """)
  int deleteForRebuild(@Param("portfolioId") UUID portfolioId,
      @Param("firstDay") LocalDate firstDay,
      @Param("from") LocalDate from);
}
