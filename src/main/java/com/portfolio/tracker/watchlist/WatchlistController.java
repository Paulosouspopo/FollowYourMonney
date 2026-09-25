package com.portfolio.tracker.watchlist;

import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.watchlist.dto.MarketDetailResponse;
import com.portfolio.tracker.watchlist.dto.PricePointResponse;
import com.portfolio.tracker.watchlist.dto.WatchlistAddRequest;
import com.portfolio.tracker.watchlist.dto.WatchlistItemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Actifs suivis et fiche d'un actif. Le symbole passe en paramètre de requête
 * (« ^FCHI », « EURUSD=X », « TTE.PA » ne tiennent pas bien dans un chemin).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping("/watchlist")
    public List<WatchlistItemResponse> list(@AuthenticationPrincipal CustomUserDetails user) {
        return watchlistService.list(user.getId());
    }

    @PostMapping("/watchlist")
    public ResponseEntity<WatchlistItemResponse> add(@Valid @RequestBody WatchlistAddRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(watchlistService.add(user.getId(), request.symbol()));
    }

    @DeleteMapping("/watchlist/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        watchlistService.remove(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/market/detail")
    public MarketDetailResponse detail(@RequestParam String symbol, @AuthenticationPrincipal CustomUserDetails user) {
        return watchlistService.detail(user.getId(), symbol);
    }

    @GetMapping("/market/history")
    public List<PricePointResponse> history(@RequestParam String symbol,
            @RequestParam(defaultValue = "1Y") String range) {
        return watchlistService.history(symbol, range);
    }
}
