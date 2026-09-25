package com.portfolio.tracker.plan;

import com.portfolio.tracker.plan.dto.PlanRequest;
import com.portfolio.tracker.plan.dto.PlanResponse;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    /** Tous les plans de l'utilisateur, prochaine échéance d'abord (dashboard). */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> all(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(planService.findAll(user.getId()));
    }

    @GetMapping("/portfolios/{portfolioId}/plans")
    public ResponseEntity<List<PlanResponse>> byPortfolio(@PathVariable UUID portfolioId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(planService.findByPortfolio(portfolioId, user.getId()));
    }

    /** Les échéances déjà passées sont exécutées aussitôt (au cours de clôture de chaque jour). */
    @PostMapping("/portfolios/{portfolioId}/plans")
    public ResponseEntity<PlanResponse> create(@PathVariable UUID portfolioId,
            @Valid @RequestBody PlanRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(portfolioId, request, user.getId()));
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<PlanResponse> update(@PathVariable UUID planId,
            @Valid @RequestBody PlanRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(planService.update(planId, request, user.getId()));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<Void> delete(@PathVariable UUID planId, @AuthenticationPrincipal CustomUserDetails user) {
        planService.delete(planId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
