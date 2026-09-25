package com.portfolio.tracker.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param symbol symbole choisi dans la recherche (jamais saisi à la main) */
public record WatchlistAddRequest(@NotBlank @Size(max = 64) String symbol) {
}
