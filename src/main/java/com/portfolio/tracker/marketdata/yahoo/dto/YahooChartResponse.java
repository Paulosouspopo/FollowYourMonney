package com.portfolio.tracker.marketdata.yahoo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result, Object error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Meta meta, List<Long> timestamp, Indicators indicators, Events events) {
    }

    /** Présent avec {@code events=div} : dividendes indexés par horodatage (secondes). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Events(Map<String, Dividend> dividends) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dividend(Double amount, Long date) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            String symbol,
            String currency,
            Double regularMarketPrice,
            String longName,
            String shortName,
            String fullExchangeName,
            String instrumentType,
            Long regularMarketTime,
            String exchangeTimezoneName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Quote> quote) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(List<Double> close) {
    } // peut contenir des null (jours sans cotation)
}