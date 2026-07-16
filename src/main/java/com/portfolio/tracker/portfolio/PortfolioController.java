package com.portfolio.tracker.portfolio;

import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.portfolio.dto.PortfolioResponse;
import com.portfolio.tracker.portfolio.dto.PortfolioUpdateRequest;
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
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> getByUser(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.findByUserId(userDetails.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PortfolioResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.findByIdAndUserId(id, userDetails.getId()));
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(
            @Valid @RequestBody PortfolioCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PortfolioResponse created = portfolioService.create(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PortfolioResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PortfolioUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(portfolioService.update(id, request, userDetails.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        portfolioService.deleteById(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
