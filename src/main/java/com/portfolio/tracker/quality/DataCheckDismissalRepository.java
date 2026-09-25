package com.portfolio.tracker.quality;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface DataCheckDismissalRepository extends JpaRepository<DataCheckDismissal, DataCheckDismissal.Key> {

    @Query("SELECT d.issueKey FROM DataCheckDismissal d WHERE d.userId = :userId")
    Set<String> findKeysByUserId(@Param("userId") UUID userId);
}
