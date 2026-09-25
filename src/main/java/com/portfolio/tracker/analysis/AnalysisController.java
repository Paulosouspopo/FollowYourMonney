package com.portfolio.tracker.analysis;

import com.portfolio.tracker.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Radiographie (expositions, frais) et contributions par ligne. `portfolioId` absent = tout le patrimoine. */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final ExposureService exposureService;
    private final ContributionService contributionService;

    @GetMapping("/exposure")
    public ExposureCalculator.Exposure exposure(@RequestParam(required = false) UUID portfolioId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return exposureService.exposure(user.getId(), portfolioId);
    }

    @GetMapping("/contributions")
    public ContributionService.Report contributions(@RequestParam(required = false) UUID portfolioId,
            @RequestParam(required = false) String period, @AuthenticationPrincipal CustomUserDetails user) {
        return contributionService.contributions(user.getId(), portfolioId, period);
    }

    /** Frais courants annuels d'un fonds : { "symbol": "ESE.PA", "annualFeePct": 0.15 } (null = ceux de Yahoo). */
    @PutMapping("/fees")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setFee(@RequestBody Map<String, Object> body, @AuthenticationPrincipal CustomUserDetails user) {
        Object fee = body.get("annualFeePct");
        exposureService.setAnnualFee(user.getId(), String.valueOf(body.get("symbol")),
                fee == null ? null : new BigDecimal(fee.toString()));
    }
}
