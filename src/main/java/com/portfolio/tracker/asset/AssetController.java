package com.portfolio.tracker.asset;

import com.portfolio.tracker.asset.dto.AssetCreateRequest;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.asset.dto.AssetUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public ResponseEntity<List<AssetResponse>> findByPortfolioIdAndUserId(
            @PathVariable UUID portfolioId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(assetService.findByPortfolioIdAndUserId(portfolioId, userId));
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<AssetResponse> findByIdAndUserId(
            @PathVariable UUID assetId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(assetService.findByIdAndUserId(assetId, userId));
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody AssetCreateRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        AssetResponse response = assetService.create(portfolioId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{assetId}")
    public ResponseEntity<AssetResponse> update(
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetUpdateRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(assetService.update(assetId, request, userId));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> deleteById(
            @PathVariable UUID assetId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        assetService.deleteById(assetId, userId);
        return ResponseEntity.noContent().build();
    }
}
