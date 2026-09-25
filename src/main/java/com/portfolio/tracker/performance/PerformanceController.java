package com.portfolio.tracker.performance;

import com.portfolio.tracker.performance.dto.PerformanceResponse;
import com.portfolio.tracker.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Performance réelle (TWR, XIRR) et comparaison à un indice.
 * {@code benchmark} : symbole Yahoo (« CW8.PA », « ^FCHI »…), facultatif.
 */
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping
    public PerformanceResponse global(@RequestParam(required = false) String period,
            @RequestParam(required = false) String benchmark,
            @AuthenticationPrincipal CustomUserDetails user) {
        return performanceService.performance(user.getId(), null, period, benchmark);
    }

    @GetMapping("/portfolios/{portfolioId}")
    public PerformanceResponse portfolio(@PathVariable UUID portfolioId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String benchmark,
            @AuthenticationPrincipal CustomUserDetails user) {
        return performanceService.performance(user.getId(), portfolioId, period, benchmark);
    }
}
