package com.portfolio.tracker.transaction;

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

    /**
     * Transactions du portefeuille (plus récentes d'abord), éventuellement
     * filtrées sur un actif.
     */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getByPortfolio(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String assetSymbol,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.findByPortfolio(portfolioId, assetSymbol, userDetails.getId()));
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
        java.util.UUID trashId = transactionService.deleteById(id, userDetails.getId());
        return ResponseEntity.noContent()
                .header(com.portfolio.tracker.trash.TrashController.TRASH_ID_HEADER, trashId.toString()).build();
    }
}
