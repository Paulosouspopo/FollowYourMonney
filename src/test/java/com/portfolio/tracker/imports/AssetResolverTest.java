package com.portfolio.tracker.imports;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.imports.dto.AssetResolutionDto;
import com.portfolio.tracker.imports.dto.AssetResolutionDto.Confidence;
import com.portfolio.tracker.marketdata.AssetSearchResult;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AssetResolver — du plus sûr au moins sûr")
class AssetResolverTest {

    private MarketDataProvider provider;
    private AssetResolver resolver;

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataProvider.class);
        when(provider.getQuote(anyString())).thenReturn(Optional.empty());
        when(provider.search(anyString())).thenReturn(List.of());
        resolver = new AssetResolver(provider);
    }

    private static MarketQuote quote(String symbol, String name) {
        return new MarketQuote(symbol, BigDecimal.ONE, "EUR", LocalDateTime.now(), LocalDate.now(), name, "CCC", "CRYPTOCURRENCY");
    }

    @Test
    @DisplayName("Correspondance mémorisée : aucun appel réseau")
    void memorise() {
        AssetResolutionDto r = resolver.resolve(AssetRef.name("BNP EASY S&P 500", "Paris"), Map.of("NAME:BNP EASY S&P 500", "ESE.PA"));
        assertThat(r.confidence()).isEqualTo(Confidence.REMEMBERED);
        assertThat(r.suggestion().symbol()).isEqualTo("ESE.PA");
        verify(provider, never()).search(anyString());
    }

    @Test
    @DisplayName("ISIN : premier résultat, considéré comme sûr")
    void isin() {
        when(provider.search("FR0000133308")).thenReturn(List.of(new AssetSearchResult("ORA.PA", "Orange", "Paris", AssetType.ACTION)));
        AssetResolutionDto r = resolver.resolve(AssetRef.isin("FR0000133308", "Orange"), Map.of());
        assertThat(r.confidence()).isEqualTo(Confidence.CERTAIN);
        assertThat(r.suggestion().symbol()).isEqualTo("ORA.PA");
    }

    @Test
    @DisplayName("Crypto : paire EUR sûre ; à défaut paire USD à confirmer ; sinon introuvable")
    void crypto() {
        when(provider.getQuote("BTC-EUR")).thenReturn(Optional.of(quote("BTC-EUR", "Bitcoin EUR")));
        when(provider.getQuote("GALA-USD")).thenReturn(Optional.of(quote("GALA-USD", "Gala USD")));

        assertThat(resolver.resolve(AssetRef.crypto("BTC", null), Map.of()).confidence()).isEqualTo(Confidence.CERTAIN);
        AssetResolutionDto gala = resolver.resolve(AssetRef.crypto("GALA", null), Map.of());
        assertThat(gala.confidence()).isEqualTo(Confidence.TO_CONFIRM);
        assertThat(gala.suggestion().symbol()).isEqualTo("GALA-USD");
        assertThat(resolver.resolve(AssetRef.crypto("XYZ", null), Map.of()).confidence()).isEqualTo(Confidence.NOT_FOUND);
    }

    @Test
    @DisplayName("Nom : libellé raccourci, place du relevé privilégiée, à confirmer")
    void nom() {
        when(provider.search("BNP PARIBAS EASY S&P 500 UCITS ETF")).thenReturn(List.of(
                new AssetSearchResult("ESEE.MI", "BNP Easy S&P 500", "Milan", AssetType.ETF),
                new AssetSearchResult("ESE.PA", "BNP Easy S&P 500", "Paris", AssetType.ETF)));

        AssetResolutionDto r = resolver.resolve(AssetRef.name("BNP PARIBAS EASY S&P 500 UCITS ETF - C EUR ACC", "Paris"), Map.of());
        assertThat(r.confidence()).isEqualTo(Confidence.TO_CONFIRM);
        assertThat(r.suggestion().symbol()).isEqualTo("ESE.PA");
    }

    @Test
    @DisplayName("Requêtes de recherche dérivées d'un libellé de courtier")
    void requetes() {
        assertThat(AssetResolver.nameQueries("Amundi PEA Nasdaq-100 UCITS ETF - EUR ACC")).containsExactly(
                "Amundi PEA Nasdaq-100 UCITS ETF", "Amundi PEA Nasdaq-100", "Amundi PEA Nasdaq-100 UCITS ETF - EUR ACC");
    }
}
