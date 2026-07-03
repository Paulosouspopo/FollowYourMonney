package com.portfolio.tracker.portfolio;

import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.portfolio.dto.PortfolioResponse;
import com.portfolio.tracker.portfolio.dto.PortfolioUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/{id}")
    public ResponseEntity<PortfolioResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(portfolioService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> getByUser(@RequestParam UUID userId) {
        return ResponseEntity.ok(portfolioService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@Valid @RequestBody PortfolioCreateRequest request) {
        PortfolioResponse created = portfolioService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PortfolioResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PortfolioUpdateRequest request) {
        return ResponseEntity.ok(portfolioService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        portfolioService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
