package com.portfolio.tracker.recurringinvestment;

import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentCreateRequest;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentResponse;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentUpdateRequest;
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
@RequestMapping("/api/recurring-investments")
@RequiredArgsConstructor
public class RecurringInvestmentController {

    private final RecurringInvestmentService recurringInvestmentService;

    @GetMapping("/{id}")
    public ResponseEntity<RecurringInvestmentResponse> getById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                recurringInvestmentService.findById(id, userDetails.getId()));
    }

    @GetMapping
    public ResponseEntity<List<RecurringInvestmentResponse>> getByAsset(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam UUID assetId) {
        return ResponseEntity.ok(
                recurringInvestmentService.findByAssetId(assetId, userDetails.getId()));
    }

    @PostMapping
    public ResponseEntity<RecurringInvestmentResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RecurringInvestmentCreateRequest request) {
        RecurringInvestmentResponse created = recurringInvestmentService.create(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringInvestmentResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody RecurringInvestmentUpdateRequest request) {
        return ResponseEntity.ok(
                recurringInvestmentService.update(id, request, userDetails.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        recurringInvestmentService.deleteById(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/all")
    public ResponseEntity<List<RecurringInvestmentResponse>> getAllByUser(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                recurringInvestmentService.findAllByUser(userDetails.getId()));
    }
}