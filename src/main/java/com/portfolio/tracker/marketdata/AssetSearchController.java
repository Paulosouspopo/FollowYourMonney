package com.portfolio.tracker.marketdata;

import java.util.List;

import org.hibernate.validator.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetSearchController {

    private final AssetSearchService assetSearchService;

    @GetMapping("/search")
    public ResponseEntity<List<AssetSearchResult>> search(
            @RequestParam("query") @NotBlank @Size(min = 2, max = 50) String query) {
        return ResponseEntity.ok(assetSearchService.search(query));
    }
}