package com.portfolio.tracker.imports;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.imports.dto.AssetResolutionDto;
import com.portfolio.tracker.imports.dto.AssetResolutionDto.Confidence;
import com.portfolio.tracker.marketdata.AssetSearchResult;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.marketdata.yahoo.YahooFinanceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Propose un symbole Yahoo pour un actif de relevé, du plus sûr au moins sûr :
 * <ol>
 * <li>correspondance déjà validée par l'utilisateur (mémoire) ;</li>
 * <li>ISIN : premier résultat de la recherche Yahoo ;</li>
 * <li>crypto : paire {@code CODE-EUR} si elle existe ; sinon {@code CODE-USD}, puis
 * recherche (à confirmer : « TAO » renvoie par exemple « Wrapped TAO ») ;</li>
 * <li>nom : recherche par libellé, en privilégiant la place du relevé (à confirmer).</li>
 * </ol>
 * L'utilisateur valide ou change toujours le symbole dans l'aperçu : il ne le
 * saisit jamais à la main.
 */
@Component
@RequiredArgsConstructor
public class AssetResolver {

    private final MarketDataProvider marketDataProvider;

    public AssetResolutionDto resolve(AssetRef ref, Map<String, String> remembered) {
        String known = remembered.get(ref.reference());
        if (known != null) {
            return result(ref, new AssetSearchResult(known, ref.label(), null, null), Confidence.REMEMBERED);
        }
        if (ref.isin() != null) {
            List<AssetSearchResult> hits = marketDataProvider.search(ref.isin());
            if (!hits.isEmpty()) {
                return result(ref, hits.get(0), Confidence.CERTAIN);
            }
        }
        if (ref.cryptoCode() != null) {
            return resolveCrypto(ref);
        }
        return resolveByName(ref);
    }

    private AssetResolutionDto resolveCrypto(AssetRef ref) {
        String code = ref.cryptoCode();
        Optional<MarketQuote> pair = marketDataProvider.getQuote(code + "-EUR");
        if (pair.isPresent()) {
            MarketQuote q = pair.get();
            AssetType type = Optional.ofNullable(YahooFinanceClient.mapAssetType(q.instrumentType())).orElse(AssetType.CRYPTO);
            return result(ref, new AssetSearchResult(q.symbol(), q.longName() != null ? q.longName() : ref.label(),
                    q.exchangeName(), type), Confidence.CERTAIN);
        }
        // Pas de paire en euros (RENDER, GALA...) : la paire en dollars porte en
        // général le même code, mais les homonymes existent (à confirmer)
        Optional<MarketQuote> usdPair = marketDataProvider.getQuote(code + "-USD");
        if (usdPair.isPresent()) {
            MarketQuote q = usdPair.get();
            return result(ref, new AssetSearchResult(q.symbol(), q.longName() != null ? q.longName() : ref.label(),
                    q.exchangeName(), AssetType.CRYPTO), Confidence.TO_CONFIRM);
        }
        List<AssetSearchResult> cryptos = marketDataProvider.search(code).stream()
                .filter(r -> r.assetType() == AssetType.CRYPTO)
                .toList();
        return cryptos.stream()
                .filter(r -> r.symbol().toUpperCase().startsWith(code + "-"))
                .findFirst()
                .or(() -> cryptos.stream().findFirst())
                .map(r -> result(ref, r, Confidence.TO_CONFIRM))
                .orElseGet(() -> result(ref, null, Confidence.NOT_FOUND));
    }

    private AssetResolutionDto resolveByName(AssetRef ref) {
        for (String query : nameQueries(ref.label())) {
            List<AssetSearchResult> hits = marketDataProvider.search(query);
            if (hits.isEmpty()) {
                continue;
            }
            AssetSearchResult best = hits.stream()
                    .filter(r -> ref.preferredExchange() != null && r.exchange() != null
                            && r.exchange().equalsIgnoreCase(ref.preferredExchange()))
                    .findFirst()
                    .orElse(hits.get(0));
            return result(ref, best, Confidence.TO_CONFIRM);
        }
        return result(ref, null, Confidence.NOT_FOUND);
    }

    /**
     * Libellés de courtier souvent trop longs pour la recherche Yahoo :
     * « BNP PARIBAS EASY S&P 500 UCITS ETF - C EUR ACC » → d'abord la partie
     * avant « - », puis sans « UCITS ETF », puis le libellé complet.
     */
    static List<String> nameQueries(String label) {
        Set<String> queries = new LinkedHashSet<>();
        String head = label.contains(" - ") ? label.substring(0, label.indexOf(" - ")).trim() : label.trim();
        queries.add(head);
        queries.add(head.replaceAll("(?i)\\s*UCITS ETF.*$", "").trim());
        queries.add(label.trim());
        List<String> result = new ArrayList<>();
        queries.stream().filter(q -> q.length() >= 2).forEach(result::add);
        return result;
    }

    private static AssetResolutionDto result(AssetRef ref, AssetSearchResult suggestion, Confidence confidence) {
        return new AssetResolutionDto(ref.reference(), ref.label(), ref.isin(), suggestion, confidence);
    }
}
