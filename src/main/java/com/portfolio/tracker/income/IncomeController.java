package com.portfolio.tracker.income;

import com.portfolio.tracker.income.dto.IncomeResponse;
import com.portfolio.tracker.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Revenus passifs : reçus, attendus, calendrier. */
@RestController
@RequestMapping("/api/income")
@RequiredArgsConstructor
public class IncomeController {

    private final IncomeService incomeService;

    @GetMapping
    public IncomeResponse income(@AuthenticationPrincipal CustomUserDetails user) {
        return incomeService.income(user.getId());
    }
}
