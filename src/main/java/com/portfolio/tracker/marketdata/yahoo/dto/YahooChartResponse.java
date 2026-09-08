package com.portfolio.tracker.marketdata.yahoo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result, Object error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Meta meta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            String currency,
            String symbol,
            String exchangeName,
            String fullExchangeName,
            String instrumentType,
            double regularMarketPrice,
            double regularMarketChangePercent,
            double fiftyTwoWeekHigh,
            double fiftyTwoWeekLow,
            String longName,
            String shortName
    ) {}
}