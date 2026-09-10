package com.portfolio.tracker.marketdata.yahoo;

import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.marketdata.yahoo.dto.YahooChartResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class YahooFinanceClient implements MarketDataProvider {

    private final RestClient restClient;

    public YahooFinanceClient(
            @Value("${app.yahoo.base-url}") String baseUrl,
            @Value("${app.yahoo.user-agent}") String userAgent
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    @Override
    public Optional<MarketQuote> getQuote(String symbol) {
        try {
            YahooChartResponse response = restClient.get()
                    .uri("/v8/finance/chart/{symbol}?range=1d&interval=1d", symbol)
                    .retrieve()
                    .body(YahooChartResponse.class);

            return parse(symbol, response);

        } catch (Exception e) {
            log.error("Yahoo Finance call failed for symbol {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<MarketQuote> parse(String symbol, YahooChartResponse response) {
        if (response == null || response.chart() == null) {
            log.warn("Empty Yahoo response for symbol {}", symbol);
            return Optional.empty();
        }

        List<YahooChartResponse.Result> results = response.chart().result();
        if (results == null || results.isEmpty()) {
            log.warn("No result in Yahoo response for symbol {} (error={})", symbol, response.chart().error());
            return Optional.empty();
        }

        YahooChartResponse.Meta meta = results.get(0).meta();
        if (meta == null) {
            log.warn("No meta in Yahoo response for symbol {}", symbol);
            return Optional.empty();
        }

        String displayName = (meta.longName() != null && !meta.longName().isBlank())
                ? meta.longName()
                : meta.shortName();

        return Optional.of(new MarketQuote(
                meta.symbol(),
                BigDecimal.valueOf(meta.regularMarketPrice()),
                meta.currency(),
                LocalDateTime.now(),
                displayName,
                meta.fullExchangeName()
        ));
    }
}
