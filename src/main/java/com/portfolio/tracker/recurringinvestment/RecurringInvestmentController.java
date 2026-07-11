package com.portfolio.tracker.recurringinvestment;

import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentCreateRequest;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentResponse;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring-investments")
@RequiredArgsConstructor
public class RecurringInvestmentController {

    private final RecurringInvestmentService recurringInvestmentService;

    @GetMapping("/{id}")
    public ResponseEntity<RecurringInvestmentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(recurringInvestmentService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<RecurringInvestmentResponse>> getByAsset(@RequestParam UUID assetId) {
        return ResponseEntity.ok(recurringInvestmentService.findByAssetId(assetId));
    }

    @PostMapping
    public ResponseEntity<RecurringInvestmentResponse> create(
            @Valid @RequestBody RecurringInvestmentCreateRequest request) {
        RecurringInvestmentResponse created = recurringInvestmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringInvestmentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody RecurringInvestmentUpdateRequest request) {
        return ResponseEntity.ok(recurringInvestmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recurringInvestmentService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}