package com.portfolio.tracker.asset;

import com.portfolio.tracker.asset.dto.AssetCreateRequest;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.asset.dto.AssetUpdateRequest;
import com.portfolio.tracker.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final AssetReplacementService assetReplacementService;

    @GetMapping
    public ResponseEntity<List<AssetResponse>> findByPortfolioIdAndUserId(
            @PathVariable UUID portfolioId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(assetService.findByPortfolioIdAndUserId(portfolioId, userDetails.getId()));
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<AssetResponse> findByIdAndUserId(
            @PathVariable UUID assetId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(assetService.findByIdAndUserId(assetId, userDetails.getId()));
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody AssetCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AssetResponse response = assetService.create(portfolioId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{assetId}")
    public ResponseEntity<AssetResponse> update(
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(assetService.update(assetId, request, userDetails.getId()));
    }

    /** Remplacer l'actif d'une ligne (opérations conservées) ; fusion si le nouvel actif est déjà présent. */
    @PostMapping("/{assetId}/replace")
    public ResponseEntity<AssetResponse> replace(@PathVariable UUID portfolioId, @PathVariable UUID assetId,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(assetReplacementService.replace(portfolioId, assetId, body.get("symbol"), user.getId()));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> deleteById(
            @PathVariable UUID assetId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        assetService.deleteById(assetId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
