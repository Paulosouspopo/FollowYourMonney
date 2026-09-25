package com.portfolio.tracker.tax;

import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.tax.dto.TaxReport;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Récapitulatif fiscal : ?year=2025 (par défaut l'année écoulée). */
@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    /** Tranche marginale d'imposition : { "marginalTaxRate": 30 }. */
    @PutMapping("/settings")
    public TaxReport settings(@RequestBody java.util.Map<String, Integer> body, @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal CustomUserDetails user) {
        Integer rate = body.get("marginalTaxRate");
        taxService.setMarginalTaxRate(user.getId(), rate != null ? rate : -1);
        return taxService.report(user.getId(), year);
    }

    @GetMapping
    public TaxReport report(@RequestParam(required = false) Integer year, @AuthenticationPrincipal CustomUserDetails user) {
        return taxService.report(user.getId(), year);
    }
}
