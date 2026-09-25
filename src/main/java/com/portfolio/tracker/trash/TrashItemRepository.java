package com.portfolio.tracker.trash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrashItemRepository extends JpaRepository<TrashItem, UUID> {

    List<TrashItem> findByUserIdAndDeletedAtAfterOrderByDeletedAtDesc(UUID userId, LocalDateTime after);

    Optional<TrashItem> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("DELETE FROM TrashItem t WHERE t.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM TrashItem t WHERE t.deletedAt < :before")
    int purgeBefore(@Param("before") LocalDateTime before);
}
