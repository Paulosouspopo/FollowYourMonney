package com.portfolio.tracker.watchlist;

import com.portfolio.tracker.assetprice.AssetPrice;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.assetprice.MarketPriceLookup;
import com.portfolio.tracker.assetprice.PriceHistoryService;
import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.dashboard.dto.PositionValuation;
import com.portfolio.tracker.dashboard.dto.ValuationResult;
import com.portfolio.tracker.exchangerate.ExchangeRateService;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.marketdata.yahoo.YahooFinanceClient;
import com.portfolio.tracker.notification.alert.AlertRule;
import com.portfolio.tracker.notification.alert.AlertRuleService;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceAlreadyExistsException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.watchlist.dto.MarketDetailResponse;
import com.portfolio.tracker.watchlist.dto.PricePointResponse;
import com.portfolio.tracker.watchlist.dto.WatchlistItemResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Actifs suivis (onglet « Marchés ») et fiche d'un actif. La liste lit la
 * base (cotations horaires) ; la fiche interroge la source de marché.
 * Méthodes non transactionnelles : les appels réseau restent hors transaction.
 */
@Service
@Slf4j
public class WatchlistService {

    private static final int MAX_ITEMS = 100;
    private static final int SPARKLINE_DAYS = 30;

    private final WatchlistRepository repository;
    private final AssetPriceRepository priceRepository;
    private final PriceHistoryService priceHistoryService;
    private final MarketPriceLookup priceLookup;
    private final MarketDataProvider marketDataProvider;
    private final ExchangeRateService exchangeRateService;
    private final PortfolioValuationService valuationService;
    private final AlertRuleService alertRuleService;
    private final TransactionTemplate tx;

    public WatchlistService(WatchlistRepository repository, AssetPriceRepository priceRepository,
            PriceHistoryService priceHistoryService, MarketPriceLookup priceLookup,
            MarketDataProvider marketDataProvider, ExchangeRateService exchangeRateService,
            PortfolioValuationService valuationService, AlertRuleService alertRuleService,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.priceRepository = priceRepository;
        this.priceHistoryService = priceHistoryService;
        this.priceLookup = priceLookup;
        this.marketDataProvider = marketDataProvider;
        this.exchangeRateService = exchangeRateService;
        this.valuationService = valuationService;
        this.alertRuleService = alertRuleService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ liste

    public List<WatchlistItemResponse> list(UUID userId) {
        List<WatchlistItem> items = repository.findByUserIdOrderByCreatedAtAsc(userId);
        if (items.isEmpty()) {
            return List.of();
        }
        Set<String> symbols = items.stream().map(WatchlistItem::getSymbol).collect(Collectors.toSet());
        LocalDate today = LocalDate.now();
        Map<String, NavigableMap<LocalDate, DailyPrice>> series = priceHistoryService.loadSeries(symbols,
                today.minusDays(SPARKLINE_DAYS), today);
        Map<String, BigDecimal> owned = ownedQuantities(userId);
        Map<String, Long> alerts = alertRuleService.findAll(userId).stream()
                .filter(r -> r.enabled() && r.symbol() != null)
                .collect(Collectors.groupingBy(r -> r.symbol(), Collectors.counting()));

        return items.stream().map(item -> {
            NavigableMap<LocalDate, DailyPrice> s = series.getOrDefault(item.getSymbol(), new TreeMap<>());
            Map.Entry<LocalDate, DailyPrice> last = s.lastEntry();
            Map.Entry<LocalDate, DailyPrice> previous = last != null ? s.lowerEntry(last.getKey()) : null;
            return new WatchlistItemResponse(
                    item.getId(),
                    item.getSymbol(),
                    item.getName(),
                    item.getAssetType(),
                    last != null ? last.getValue().price() : null,
                    last != null ? last.getValue().currency() : null,
                    last != null ? last.getKey() : null,
                    previous != null ? change(last.getValue().price(), previous.getValue().price()) : null,
                    s.tailMap(today.minusDays(SPARKLINE_DAYS), true).values().stream().map(DailyPrice::price).toList(),
                    owned.getOrDefault(item.getSymbol(), BigDecimal.ZERO),
                    alerts.getOrDefault(item.getSymbol(), 0L).intValue());
        }).toList();
    }

    /** Suit un actif : cotation vérifiée, historique d'un an téléchargé (sparkline, records). */
    public WatchlistItemResponse add(UUID userId, String rawSymbol) {
        String requested = rawSymbol.trim().toUpperCase();
        if (repository.countByUserId(userId) >= MAX_ITEMS) {
            throw new BadRequestException("Tu suis déjà " + MAX_ITEMS + " actifs : retires-en avant d'en ajouter");
        }
        MarketQuote quote = marketDataProvider.getQuote(requested)
                .orElseThrow(() -> new BadRequestException("Actif introuvable : " + requested));
        if (repository.findByUserIdAndSymbol(userId, quote.symbol()).isPresent()) {
            throw new ResourceAlreadyExistsException(quote.symbol() + " est déjà dans ta liste");
        }
        priceHistoryService.saveQuote(quote.symbol(), quote);
        priceHistoryService.ensureCoverage(quote.symbol(), LocalDate.now().minusYears(1));
        WatchlistItem saved = tx.execute(s -> repository.save(WatchlistItem.builder()
                .userId(userId)
                .symbol(quote.symbol())
                .name(quote.longName() != null ? quote.longName() : quote.symbol())
                .assetType(YahooFinanceClient.mapAssetType(quote.instrumentType()))
                .build()));
        return list(userId).stream().filter(i -> i.id().equals(saved.getId())).findFirst().orElseThrow();
    }

    public void remove(UUID id, UUID userId) {
        tx.executeWithoutResult(s -> repository.delete(repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Actif suivi introuvable"))));
    }

    // ------------------------------------------------------------------ fiche

    public MarketDetailResponse detail(UUID userId, String rawSymbol) {
        String requested = rawSymbol.trim().toUpperCase();
        MarketQuote quote = marketDataProvider.getQuote(requested)
                .orElseThrow(() -> new ResourceNotFoundException("Actif introuvable : " + requested));
        String symbol = quote.symbol();
        priceHistoryService.saveQuote(symbol, quote);

        LocalDate today = quote.marketDate() != null ? quote.marketDate() : LocalDate.now();
        Optional<MarketPriceLookup.ClosingRange> year = priceLookup.closingRange(symbol, today.minusYears(1),
                today.plusDays(1));
        BigDecimal previousClose = priceHistoryService.findOnOrBefore(symbol, today.minusDays(1))
                .map(DailyPrice::price).orElse(null);
        BigDecimal priceEur = quote.price().multiply(
                exchangeRateService.getRateAsOf(quote.currency(), MoneyConstants.BASE_CURRENCY, LocalDate.now()));

        List<MarketDetailResponse.Holding> holdings = holdings(userId, symbol);
        return new MarketDetailResponse(
                symbol,
                quote.longName() != null ? quote.longName() : symbol,
                quote.exchangeName(),
                YahooFinanceClient.mapAssetType(quote.instrumentType()),
                quote.price(),
                quote.currency(),
                priceEur.setScale(6, RoundingMode.HALF_UP),
                quote.asOf(),
                quote.marketDate(),
                previousClose != null ? change(quote.price(), previousClose) : null,
                year.map(MarketPriceLookup.ClosingRange::low).map(low -> low.min(quote.price())).orElse(null),
                year.map(MarketPriceLookup.ClosingRange::high).map(high -> high.max(quote.price())).orElse(null),
                repository.findByUserIdAndSymbol(userId, symbol).map(WatchlistItem::getId).orElse(null),
                holdings,
                alertRuleService.findAll(userId).stream()
                        .filter(r -> r.scope() == AlertRule.Scope.ASSET && symbol.equals(r.symbol()))
                        .toList());
    }

    /** Clôtures quotidiennes pour le graphique (téléchargées au besoin). */
    public List<PricePointResponse> history(String rawSymbol, String rangeCode) {
        String symbol = rawSymbol.trim().toUpperCase();
        LocalDate today = LocalDate.now();
        LocalDate from = MarketRange.fromCode(rangeCode).from(today);
        priceHistoryService.ensureCoverage(symbol, from);
        return priceRepository.findBySymbolInAndPriceDateBetweenOrderByPriceDateAsc(List.of(symbol), from, today)
                .stream()
                .map((AssetPrice p) -> new PricePointResponse(p.getPriceDate(), p.getPrice()))
                .toList();
    }

    // ---------------------------------------------------------------- détenus

    private List<MarketDetailResponse.Holding> holdings(UUID userId, String symbol) {
        ValuationResult valuation = tx.execute(s -> valuationService.valuate(userId, null, null));
        List<MarketDetailResponse.Holding> out = new ArrayList<>();
        for (PortfolioValuation p : valuation.getPortfolios()) {
            if (p.getPositions() == null) {
                continue;
            }
            for (PositionValuation pos : p.getPositions()) {
                if (symbol.equals(pos.getSymbol()) && pos.getQuantity() != null && pos.getQuantity().signum() > 0) {
                    out.add(new MarketDetailResponse.Holding(p.getPortfolioId(), p.getName(), pos.getQuantity(),
                            pos.getCurrentValueEur(), pos.getInvestedEur(), pos.getUnrealizedGainEur(),
                            pos.getUnrealizedGainPercentage()));
                }
            }
        }
        return out;
    }

    private Map<String, BigDecimal> ownedQuantities(UUID userId) {
        ValuationResult valuation = tx.execute(s -> valuationService.valuate(userId, null, null));
        Map<String, BigDecimal> owned = new HashMap<>();
        for (PortfolioValuation p : valuation.getPortfolios()) {
            if (p.getPositions() == null) {
                continue;
            }
            for (PositionValuation pos : p.getPositions()) {
                if (pos.getQuantity() != null && pos.getQuantity().signum() > 0) {
                    owned.merge(pos.getSymbol(), pos.getQuantity(), BigDecimal::add);
                }
            }
        }
        return owned;
    }

    private static BigDecimal change(BigDecimal now, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return now.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }
}
