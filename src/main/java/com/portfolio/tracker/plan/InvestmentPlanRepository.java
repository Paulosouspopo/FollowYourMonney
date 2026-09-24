package com.portfolio.tracker.plan;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentPlanRepository extends JpaRepository<InvestmentPlan, UUID> {

    @Query("""
            SELECT p FROM InvestmentPlan p JOIN FETCH p.portfolio pf
            WHERE pf.user.id = :userId
            ORDER BY p.nextExecutionDate ASC NULLS LAST, p.createdAt ASC
            """)
    List<InvestmentPlan> findByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT p FROM InvestmentPlan p JOIN FETCH p.portfolio pf
            WHERE pf.id = :portfolioId AND pf.user.id = :userId
            ORDER BY p.createdAt ASC
            """)
    List<InvestmentPlan> findByPortfolioIdAndUserId(@Param("portfolioId") UUID portfolioId,
                                                    @Param("userId") UUID userId);

    @Query("SELECT p FROM InvestmentPlan p JOIN FETCH p.portfolio pf WHERE p.id = :id AND pf.user.id = :userId")
    Optional<InvestmentPlan> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Plans actifs ayant une échéance passée ou du jour. */
    @Query("SELECT p.id FROM InvestmentPlan p WHERE p.active = true AND p.nextExecutionDate <= :day")
    List<UUID> findDueIds(@Param("day") LocalDate day);

    /** Verrou : le job du soir et une modification utilisateur n'exécutent pas deux fois la même échéance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM InvestmentPlan p JOIN FETCH p.portfolio pf JOIN FETCH pf.user WHERE p.id = :id")
    Optional<InvestmentPlan> findByIdForUpdate(@Param("id") UUID id);
}
