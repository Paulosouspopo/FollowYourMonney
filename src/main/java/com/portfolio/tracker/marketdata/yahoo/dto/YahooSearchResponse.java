package com.portfolio.tracker.marketdata.yahoo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooSearchResponse(List<Quote> quotes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(
            String symbol,
            String shortname,
            String longname,
            String quoteType, // EQUITY, ETF, CRYPTOCURRENCY, MUTUALFUND, INDEX, FUTURE, CURRENCY...
            String exchange, // PAR, NMS, NYQ, CCC...
            String exchDisp, // "Paris", "NASDAQ", "CCC"
            Double score,
            Boolean isYahooFinance) {
    }
}