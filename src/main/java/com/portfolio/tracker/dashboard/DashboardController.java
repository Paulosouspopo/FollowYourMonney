package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.dashboard.dto.DashboardSummaryDTO;
import com.portfolio.tracker.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard
     * Retourne le dashboard global de l'utilisateur connecté
     */
    @GetMapping
    public ResponseEntity<DashboardSummaryDTO> getUserDashboard(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        DashboardSummaryDTO dashboard = dashboardService.getUserDashboard(userDetails.getId());
        return ResponseEntity.ok(dashboard);
    }
}