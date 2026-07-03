package com.portfolio.tracker.asset;

import com.portfolio.tracker.asset.dto.AssetCreateRequest;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.asset.dto.AssetUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<AssetResponse>> getByPortfolio(@RequestParam UUID portfolioId) {
        return ResponseEntity.ok(assetService.findByPortfolioId(portfolioId));
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetCreateRequest request) {
        AssetResponse created = assetService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssetResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AssetUpdateRequest request) {
        return ResponseEntity.ok(assetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assetService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
