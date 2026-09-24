package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.dashboard.dto.DashboardResponse;
import com.portfolio.tracker.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** {@code currency} : devise de la courbe (taux historique de chaque jour), EUR par défaut. */
    @GetMapping
    public DashboardResponse getDashboard(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String currency,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return dashboardService.getDashboard(userDetails.getId(), period, currency);
    }

    @GetMapping("/portfolios/{portfolioId}")
    public DashboardResponse getPortfolioDashboard(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String currency,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return dashboardService.getPortfolioDashboard(userDetails.getId(), portfolioId, period, currency);
    }
}