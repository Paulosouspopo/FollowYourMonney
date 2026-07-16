package com.portfolio.tracker.transaction;

import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import com.portfolio.tracker.transaction.dto.TransactionUpdateRequest;
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
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getByAsset(
            @RequestParam UUID assetId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(transactionService.findByAssetId(assetId, userDetails.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(transactionService.findByIdAndUserId(id, userDetails.getId()));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TransactionResponse created = transactionService.create(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(transactionService.update(id, request, userDetails.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        transactionService.deleteById(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
