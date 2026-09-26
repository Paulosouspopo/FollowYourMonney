package com.portfolio.tracker.marketdata.yahoo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.marketdata.AssetProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Yahoo quoteSummary : lecture d'un profil")
class YahooQuoteSummaryClientTest {

    @Test
    @DisplayName("ETF : secteurs, lignes, répartition, frais en % ; action : pays et secteur")
    void parse() throws Exception {
        String etf = """
                {"assetProfile": {}, "quoteType": {"quoteType": "ETF", "longName": "Amundi MSCI World"},
                 "fundProfile": {"feesExpensesInvestment": {"annualReportExpenseRatio": {"raw": 0.0038, "fmt": "0.38%"}}},
                 "topHoldings": {"stockPosition": {"raw": 0.9996}, "bondPosition": {"raw": 0},
                   "sectorWeightings": [{"technology": {"raw": 0.3062}}, {"realestate": {"raw": 0.0167}}, {"energy": {"raw": 0}}],
                   "holdings": [{"symbol": "AAPL", "holdingName": "Apple Inc", "holdingPercent": {"raw": 0.052}}]}}
                """;
        AssetProfile p = YahooQuoteSummaryClient.parse(new ObjectMapper().readTree(etf));
        assertThat(p.quoteType()).isEqualTo("ETF");
        assertThat(p.expenseRatioPct()).isEqualTo(0.38, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(p.sectorWeights()).containsOnlyKeys("technology", "realestate");
        assertThat(p.holdings()).singleElement().satisfies(h -> assertThat(h.weight()).isEqualTo(0.052));
        assertThat(p.stockPct()).isEqualTo(0.9996);

        AssetProfile stock = YahooQuoteSummaryClient.parse(new ObjectMapper().readTree("""
                {"assetProfile": {"country": "France", "sector": "Basic Materials"}, "quoteType": {"quoteType": "EQUITY"}}
                """));
        assertThat(stock.country()).isEqualTo("France");
        assertThat(stock.sector()).isEqualTo("Basic Materials");
        assertThat(stock.expenseRatioPct()).isNull();
        assertThat(stock.sectorWeights()).isEmpty();
    }
}
