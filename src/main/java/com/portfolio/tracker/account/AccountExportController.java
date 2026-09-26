package com.portfolio.tracker.account;

import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.shared.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

/** Export de mes données (RGPD) : JSON complet, ou opérations en CSV. */
@RestController
@RequestMapping("/api/account/export")
@RequiredArgsConstructor
public class AccountExportController {

    private final AccountExportService service;
    private final RateLimiter rateLimiter;

    @GetMapping
    public ResponseEntity<AccountExportService.Export> json(@AuthenticationPrincipal CustomUserDetails user) {
        rateLimiter.check("export:" + user.getId(), 20, Duration.ofHours(1));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"followyourmoney-" + LocalDate.now() + ".json\"")
                .body(service.export(user.getId()));
    }

    @GetMapping("/transactions.csv")
    public ResponseEntity<byte[]> csv(@AuthenticationPrincipal CustomUserDetails user) {
        rateLimiter.check("export:" + user.getId(), 20, Duration.ofHours(1));
        // BOM : Excel lit l'UTF-8 correctement
        byte[] body = ("﻿" + service.transactionsCsv(user.getId())).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"operations-" + LocalDate.now() + ".csv\"")
                .body(body);
    }
}
