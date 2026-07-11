package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.assetprice.dto.AssetPriceIngestRequest;
import com.portfolio.tracker.assetprice.dto.AssetPriceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/asset-prices")
@RequiredArgsConstructor
public class AssetPriceController {

    private final AssetPriceService assetPriceService;

    @GetMapping("/{symbol}/latest")
    public ResponseEntity<AssetPriceResponse> getLatest(@PathVariable String symbol) {
        return ResponseEntity.ok(assetPriceService.findLatestBySymbol(symbol));
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<List<AssetPriceResponse>> getHistory(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        if (start != null && end != null) {
            return ResponseEntity.ok(assetPriceService.findHistory(symbol, start, end));
        }
        return ResponseEntity.ok(assetPriceService.findAllHistory(symbol));
    }

    @PostMapping
    public ResponseEntity<AssetPriceResponse> ingest(@Valid @RequestBody AssetPriceIngestRequest request) {
        AssetPriceResponse created = assetPriceService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}