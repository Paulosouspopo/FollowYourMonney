package com.portfolio.tracker.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, UUID> {

    List<WatchlistItem> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<WatchlistItem> findByIdAndUserId(UUID id, UUID userId);

    Optional<WatchlistItem> findByUserIdAndSymbol(UUID userId, String symbol);

    long countByUserId(UUID userId);

    /** Symboles suivis par au moins un utilisateur : cotés chaque heure comme les actifs détenus. */
    @Query("SELECT DISTINCT w.symbol FROM WatchlistItem w")
    List<String> findAllDistinctSymbols();
}
