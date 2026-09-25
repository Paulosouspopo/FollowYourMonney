package com.portfolio.tracker.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.asset.ManualAssets;
import com.portfolio.tracker.marketdata.AssetProfile;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Profils de marché (pays, secteurs, lignes des fonds, frais), mis en cache :
 * 30 jours s'ils sont connus, 1 jour après un échec. Les appels réseau ont lieu
 * hors transaction ; chaque profil est enregistré dès qu'il arrive.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetProfileService {

    static final int FRESH_DAYS = 30;
    static final int RETRY_DAYS = 1;

    private final AssetProfileRepository repository;
    private final MarketDataProvider marketDataProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Profils connus des symboles demandés (absents : inconnus de Yahoo, cryptos, actifs non cotés). */
    public Map<String, AssetProfile> profiles(Collection<String> symbols) {
        Map<String, AssetProfileEntity> cached = new HashMap<>();
        repository.findAllById(symbols.stream().filter(s -> !ManualAssets.isManual(s)).distinct().toList())
                .forEach(e -> cached.put(e.getSymbol(), e));
        LocalDateTime now = LocalDateTime.now();
        Map<String, AssetProfile> out = new HashMap<>();
        for (String symbol : symbols) {
            if (ManualAssets.isManual(symbol) || out.containsKey(symbol)) {
                continue;
            }
            AssetProfileEntity e = cached.get(symbol);
            boolean stale = e == null || e.getFetchedAt().isBefore(now.minusDays(e.isOk() ? FRESH_DAYS : RETRY_DAYS));
            if (stale) {
                e = refresh(symbol);
            }
            if (e != null && e.isOk()) {
                out.put(symbol, toProfile(e));
            }
        }
        return out;
    }

    private AssetProfileEntity refresh(String symbol) {
        Optional<AssetProfile> fetched = marketDataProvider.getProfile(symbol);
        AssetProfileEntity e = fetched.map(p -> toEntity(symbol, p))
                .orElseGet(() -> AssetProfileEntity.builder().symbol(symbol).ok(false).build());
        e.setFetchedAt(LocalDateTime.now());
        try {
            return repository.save(e);
        } catch (Exception ex) {
            log.warn("Profil {} non enregistré : {}", symbol, ex.getMessage());
            return e;
        }
    }

    private AssetProfileEntity toEntity(String symbol, AssetProfile p) {
        try {
            return AssetProfileEntity.builder().symbol(symbol).quoteType(p.quoteType()).longName(p.longName())
                    .country(p.country()).sector(p.sector())
                    .sectorWeights(p.sectorWeights().isEmpty() ? null : mapper.writeValueAsString(p.sectorWeights()))
                    .holdings(p.holdings().isEmpty() ? null : mapper.writeValueAsString(p.holdings()))
                    .stockPct(dec(p.stockPct(), 4)).bondPct(dec(p.bondPct(), 4)).cashPct(dec(p.cashPct(), 4))
                    .otherPct(dec(p.otherPct(), 4)).expenseRatioPct(dec(p.expenseRatioPct(), 3)).ok(true).build();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private AssetProfile toProfile(AssetProfileEntity e) {
        try {
            Map<String, Double> sectors = e.getSectorWeights() == null ? Map.of()
                    : mapper.readValue(e.getSectorWeights(), new TypeReference<Map<String, Double>>() { });
            List<AssetProfile.Holding> holdings = e.getHoldings() == null ? List.of()
                    : mapper.readValue(e.getHoldings(), new TypeReference<List<AssetProfile.Holding>>() { });
            return new AssetProfile(e.getQuoteType(), e.getLongName(), e.getCountry(), e.getSector(), sectors, holdings,
                    dbl(e.getStockPct()), dbl(e.getBondPct()), dbl(e.getCashPct()), dbl(e.getOtherPct()),
                    dbl(e.getExpenseRatioPct()));
        } catch (Exception ex) {
            log.warn("Profil {} illisible : {}", e.getSymbol(), ex.getMessage());
            return new AssetProfile(e.getQuoteType(), e.getLongName(), e.getCountry(), e.getSector(), Map.of(), List.of(),
                    null, null, null, null, dbl(e.getExpenseRatioPct()));
        }
    }

    private static BigDecimal dec(Double v, int scale) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    private static Double dbl(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
