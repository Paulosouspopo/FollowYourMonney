package com.portfolio.tracker.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    @Query("SELECT g FROM Goal g LEFT JOIN FETCH g.portfolio WHERE g.userId = :userId ORDER BY g.createdAt ASC")
    List<Goal> findByUserId(@Param("userId") UUID userId);

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
