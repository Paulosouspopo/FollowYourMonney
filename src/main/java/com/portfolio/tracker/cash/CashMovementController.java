package com.portfolio.tracker.cash;

import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.cash.dto.CashMovementResponse;
import com.portfolio.tracker.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/cash-movements")
@RequiredArgsConstructor
public class CashMovementController {

    private final CashMovementService service;

    /** Plus récents d'abord. */
    @GetMapping
    public ResponseEntity<List<CashMovementResponse>> list(@PathVariable UUID portfolioId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(service.findByPortfolio(portfolioId, user.getId()));
    }

    @PostMapping
    public ResponseEntity<CashMovementResponse> create(@PathVariable UUID portfolioId,
            @Valid @RequestBody CashMovementRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(portfolioId, request, user.getId()));
    }

    @PutMapping("/{movementId}")
    public ResponseEntity<CashMovementResponse> update(@PathVariable UUID portfolioId,
            @PathVariable UUID movementId,
            @Valid @RequestBody CashMovementRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(service.update(portfolioId, movementId, request, user.getId()));
    }

    @DeleteMapping("/{movementId}")
    public ResponseEntity<Void> delete(@PathVariable UUID portfolioId,
            @PathVariable UUID movementId,
            @AuthenticationPrincipal CustomUserDetails user) {
        service.delete(portfolioId, movementId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
