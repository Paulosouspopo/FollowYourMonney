package com.portfolio.tracker.assetprice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@Slf4j
public class AlphaVantageClient {

    private final RestTemplate restTemplate;

    @Value("${asset.price.alpha-vantage.api-key}")
    private String apiKey;

    @Value("${asset.price.alpha-vantage.base-url}")
    private String baseUrl;

    public AlphaVantageClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Récupère le prix actuel d'une action via Alpha Vantage
     * @param symbol Symbole de l'action (ex: AAPL, MSFT)
     * @return Prix actuel
     */
    public BigDecimal getStockPrice(String symbol) {
        try {
            log.debug("Fetching price for stock symbol: {}", symbol);

            String url = String.format(
                    "%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                    baseUrl,
                    symbol,
                    apiKey
            );

            AlphaVantageResponse response = restTemplate.getForObject(url, AlphaVantageResponse.class);

            if (response == null || response.getQuoteData() == null) {
                log.warn("No data returned for symbol: {}", symbol);
                return null;
            }

            QuoteData quote = response.getQuoteData();
            if (quote.getPrice() == null || quote.getPrice().isEmpty()) {
                log.warn("No price data for symbol: {}", symbol);
                return null;
            }

            BigDecimal price = new BigDecimal(quote.getPrice());
            log.info("Successfully retrieved price for {}: {}", symbol, price);
            return price;

        } catch (Exception e) {
            log.error("Error fetching price for symbol {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    // ========== DTOs ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AlphaVantageResponse {
        @JsonProperty("Global Quote")
        private QuoteData quoteData;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteData {
        @JsonProperty("01. symbol")
        private String symbol;

        @JsonProperty("05. price")
        private String price;

        @JsonProperty("06. volume")
        private String volume;

        @JsonProperty("07. latest trading day")
        private String latestTradingDay;

        @JsonProperty("09. change")
        private String change;

        @JsonProperty("10. change percent")
        private String changePercent;
    }
}