package com.portfolio.tracker.quality;

import com.portfolio.tracker.quality.dto.DataIssue;
import com.portfolio.tracker.quality.dto.DataWarning;
import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Détection des erreurs de saisie : pendant la saisie, et audit de l'existant. */
@RestController
@RequestMapping("/api/data-checks")
@RequiredArgsConstructor
public class DataQualityController {

    private final DataQualityService service;

    @GetMapping
    public List<DataIssue> audit(@AuthenticationPrincipal CustomUserDetails user) {
        return service.audit(user.getId());
    }

    @GetMapping("/transaction")
    public List<DataWarning> checkTransaction(@RequestParam UUID portfolioId, @RequestParam String symbol,
            @RequestParam TransactionType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BigDecimal price, @RequestParam(defaultValue = "EUR") String currency,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.checkTransaction(user.getId(), portfolioId, symbol.trim(), type, date, price,
                currency.trim().toUpperCase());
    }

    /** « C'est normal » : ce contrôle ne réapparaîtra plus. */
    @PostMapping("/dismiss")
    public ResponseEntity<Void> dismiss(@RequestBody Map<String, String> body, @AuthenticationPrincipal CustomUserDetails user) {
        String key = body.get("key");
        if (key == null || key.isBlank() || key.length() > 120) {
            throw new BadRequestException("Contrôle inconnu");
        }
        service.dismiss(user.getId(), key);
        return ResponseEntity.noContent().build();
    }
}
