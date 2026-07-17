package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.dto.AvailableAssetResponse;
import com.portfolio.tracker.asset.external.AssetExternalService;
import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import com.portfolio.tracker.transaction.dto.TransactionUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final AssetExternalService assetExternalService;

    /**
     * Récupère les assets disponibles pour ajouter une transaction
     */
    @GetMapping("/available-assets")
    public ResponseEntity<List<AvailableAssetResponse>> getAvailableAssets() {
        List<AvailableAssetResponse> assets = assetExternalService.getAvailableAssets().stream()
                .map(template -> AvailableAssetResponse.builder()
                        .symbol(template.getSymbol())
                        .name(template.getName())
                        .type(template.getType())
                        .currency(template.getCurrency())
                        .build())
                .toList();
        return ResponseEntity.ok(assets);
    }

    /**
     * Récupère les transactions d'un asset
     */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getByAsset(
            @PathVariable UUID portfolioId,
            @RequestParam String assetSymbol,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.findByAssetSymbolAndPortfolioId(assetSymbol, portfolioId, userDetails.getId()));
    }

    /**
     * Récupère une transaction spécifique
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
            @PathVariable UUID portfolioId,
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(transactionService.findByIdAndUserId(id, userDetails.getId()));
    }

    /**
     * Crée une nouvelle transaction (crée ou met à jour l'asset automatiquement)
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody TransactionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TransactionResponse created = transactionService.create(portfolioId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Met à jour une transaction existante
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable UUID portfolioId,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(transactionService.update(id, request, userDetails.getId()));
    }

    /**
     * Supprime une transaction
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID portfolioId,
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        transactionService.deleteById(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
