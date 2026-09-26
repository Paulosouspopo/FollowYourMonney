package com.portfolio.tracker.asset.manual;

import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Actifs non cotés et leurs valeurs saisies. */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
@RequiredArgsConstructor
public class ManualAssetController {

    private final ManualAssetService service;

    @GetMapping("/manual-assets")
    public List<AssetResponse> list(@PathVariable UUID portfolioId, @AuthenticationPrincipal CustomUserDetails user) {
        return service.list(portfolioId, user.getId());
    }

    @PostMapping("/manual-assets")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse create(@PathVariable UUID portfolioId, @Valid @RequestBody ManualAssetRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.create(portfolioId, request, user.getId());
    }

    @GetMapping("/assets/{assetId}/valuations")
    public List<ValuationResponse> valuations(@PathVariable UUID portfolioId, @PathVariable UUID assetId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.valuations(portfolioId, assetId, user.getId());
    }

    @PutMapping("/assets/{assetId}/valuations")
    public ValuationResponse save(@PathVariable UUID portfolioId, @PathVariable UUID assetId,
            @Valid @RequestBody ValuationRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return service.saveValuation(portfolioId, assetId, request, user.getId());
    }

    @DeleteMapping("/assets/{assetId}/valuations/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID portfolioId, @PathVariable UUID assetId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails user) {
        service.deleteValuation(portfolioId, assetId, date, user.getId());
    }
}
