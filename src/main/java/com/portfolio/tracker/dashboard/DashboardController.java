package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.dashboard.dto.DashboardSummaryDTO;
import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.snapshot.PortfolioSnapshotService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PortfolioSnapshotService snapshotService;

    @GetMapping
    public ResponseEntity<DashboardSummaryDTO> getUserDashboard(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return ResponseEntity.ok(dashboardService.getUserDashboard(userDetails.getId()));
    }

    /**
     * Déclenche manuellement un snapshot — pratique en dev pour amorcer la courbe.
     */
    @PostMapping("/snapshots/trigger")
    public ResponseEntity<Void> triggerSnapshot() {
        snapshotService.createDailySnapshots();
        return ResponseEntity.accepted().build();
    }
}