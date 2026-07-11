package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.assetprice.dto.AssetPriceIngestRequest;
import com.portfolio.tracker.assetprice.dto.AssetPriceResponse;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetPriceService {

    private final AssetPriceRepository assetPriceRepository;
    private final AssetPriceMapper assetPriceMapper;

    public AssetPriceResponse findLatestBySymbol(String symbol) {
        AssetPrice assetPrice = assetPriceRepository.findFirstBySymbolOrderByLastUpdatedDesc(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("AssetPrice pour le symbole", symbol));
        return assetPriceMapper.toResponse(assetPrice);
    }

    public List<AssetPriceResponse> findHistory(String symbol, LocalDateTime start, LocalDateTime end) {
        return assetPriceRepository
                .findBySymbolAndLastUpdatedBetweenOrderByLastUpdatedAsc(symbol, start, end)
                .stream()
                .map(assetPriceMapper::toResponse)
                .toList();
    }

    public List<AssetPriceResponse> findAllHistory(String symbol) {
        return assetPriceRepository.findBySymbolOrderByLastUpdatedDesc(symbol).stream()
                .map(assetPriceMapper::toResponse)
                .toList();
    }

    @Transactional
    public AssetPriceResponse ingest(AssetPriceIngestRequest request) {
        AssetPrice assetPrice = assetPriceMapper.toEntity(request);
        AssetPrice saved = assetPriceRepository.save(assetPrice);
        return assetPriceMapper.toResponse(saved);
    }
}