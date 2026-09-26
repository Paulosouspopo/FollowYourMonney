package com.portfolio.tracker.marketdata.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.marketdata.AssetProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Yahoo quoteSummary (profil d'un actif : pays, secteur, répartition d'un
 * fonds, frais). Contrairement aux cours, cet endpoint exige un cookie de
 * session et un jeton « crumb » : obtenus une fois (fc.yahoo.com puis
 * /v1/test/getcrumb), renouvelés si Yahoo les refuse.
 */
@Component
@Slf4j
public class YahooQuoteSummaryClient {

    private static final String MODULES = "assetProfile,topHoldings,fundProfile,quoteType";
    /** L'agent minimal des cours ne suffit pas ici : Yahoo attend un navigateur. */
    private static final String BROWSER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String crumb;

    public YahooQuoteSummaryClient(@Value("${app.yahoo.base-url}") String baseUrl,
            @Value("${app.yahoo.timeout-seconds:10}") int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Profil de l'actif ; vide si Yahoo n'en a pas ou ne répond pas (jamais d'exception). */
    public Optional<AssetProfile> profile(String symbol) {
        try {
            HttpResponse<String> response = fetch(symbol, false);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                response = fetch(symbol, true); // crumb expiré : on le renouvelle une fois
            }
            if (response.statusCode() != 200) {
                log.warn("Profil Yahoo {} : HTTP {}", symbol, response.statusCode());
                return Optional.empty();
            }
            JsonNode result = mapper.readTree(response.body()).path("quoteSummary").path("result");
            return result.isArray() && !result.isEmpty() ? Optional.of(parse(result.get(0))) : Optional.empty();
        } catch (Exception e) {
            log.warn("Profil Yahoo {} indisponible : {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpResponse<String> fetch(String symbol, boolean refreshCrumb) throws Exception {
        String c = crumb(refreshCrumb);
        String url = baseUrl + "/v10/finance/quoteSummary/" + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                + "?modules=" + MODULES + "&crumb=" + URLEncoder.encode(c, StandardCharsets.UTF_8);
        return http.send(request(url), HttpResponse.BodyHandlers.ofString());
    }

    private synchronized String crumb(boolean refresh) throws Exception {
        if (crumb != null && !refresh) {
            return crumb;
        }
        // Le cookie de session est posé par fc.yahoo.com (qui répond 404 : c'est normal)
        http.send(request("https://fc.yahoo.com"), HttpResponse.BodyHandlers.discarding());
        HttpResponse<String> r = http.send(request(baseUrl + "/v1/test/getcrumb"), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200 || r.body().isBlank() || r.body().contains("<")) {
            throw new IllegalStateException("crumb Yahoo refusé (HTTP " + r.statusCode() + ")");
        }
        crumb = r.body().trim();
        return crumb;
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", BROWSER_AGENT).header("Accept", "application/json,text/plain,*/*").GET().build();
    }

    // ---------------------------------------------------------------- lecture

    static AssetProfile parse(JsonNode r) {
        JsonNode ap = r.path("assetProfile");
        JsonNode th = r.path("topHoldings");
        JsonNode qt = r.path("quoteType");

        Map<String, Double> sectors = new LinkedHashMap<>();
        for (JsonNode entry : th.path("sectorWeightings")) {
            for (Map.Entry<String, JsonNode> f : entry.properties()) {
                Double v = raw(f.getValue());
                if (v != null && v > 0) {
                    sectors.put(f.getKey(), v);
                }
            }
        }
        List<AssetProfile.Holding> holdings = new ArrayList<>();
        for (JsonNode h : th.path("holdings")) {
            Double w = raw(h.path("holdingPercent"));
            String name = text(h.path("holdingName"));
            if (w != null && w > 0 && name != null) {
                holdings.add(new AssetProfile.Holding(text(h.path("symbol")), name, w));
            }
        }
        Double ter = raw(r.path("fundProfile").path("feesExpensesInvestment").path("annualReportExpenseRatio"));
        return new AssetProfile(text(qt.path("quoteType")), text(qt.path("longName")), text(ap.path("country")),
                text(ap.path("sector")), sectors, holdings,
                raw(th.path("stockPosition")), raw(th.path("bondPosition")), raw(th.path("cashPosition")),
                raw(th.path("otherPosition")), ter != null ? ter * 100 : null);
    }

    /** Valeur numérique Yahoo : {"raw": 0.38, "fmt": "0.38%"} ou nombre simple. */
    private static Double raw(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        JsonNode v = n.isObject() ? n.path("raw") : n;
        return v.isNumber() ? v.asDouble() : null;
    }

    private static String text(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() || n.asText().isBlank() ? null : n.asText();
    }
}
