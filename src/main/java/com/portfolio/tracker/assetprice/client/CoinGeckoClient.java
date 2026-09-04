package com.portfolio.tracker.assetprice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class CoinGeckoClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${asset.price.coingecko.base-url}")
    private String baseUrl;

    @Value("${asset.price.coingecko.api-key}")
    private String apiKey;

    // Mapping des symboles crypto vers IDs CoinGecko
    private static final Map<String, String> CRYPTO_ID_MAP = Map.ofEntries(
            Map.entry("BTC", "bitcoin"),
            Map.entry("ETH", "ethereum"),
            Map.entry("USDT", "tether"),
            Map.entry("BNB", "binancecoin"),
            Map.entry("XRP", "ripple"),
            Map.entry("ADA", "cardano"),
            Map.entry("SOL", "solana"),
            Map.entry("DOT", "polkadot"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("MATIC", "matic-network"),
            Map.entry("LINK", "chainlink"),
            Map.entry("UNI", "uniswap"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("ATOM", "cosmos"),
            Map.entry("LTC", "litecoin"),
            Map.entry("BCH", "bitcoin-cash"),
            Map.entry("XLM", "stellar"),
            Map.entry("ALGO", "algorand"),
            Map.entry("VET", "vechain"),
            Map.entry("FIL", "filecoin")
    );

    public CoinGeckoClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Récupère le prix actuel d'une crypto via CoinGecko
     * @param symbol Symbole de la crypto (ex: BTC, ETH)
     * @param vsCurrency Devise cible (ex: usd, eur)
     * @return Prix actuel
     */
    public BigDecimal getCryptoPrice(String symbol, String vsCurrency) {
        try {
            String cryptoId = CRYPTO_ID_MAP.get(symbol.toUpperCase());

            if (cryptoId == null) {
                log.warn("Crypto symbol not found in mapping: {}", symbol);
                return null;
            }

            log.debug("Fetching price for crypto: {} (id: {})", symbol, cryptoId);

            String url = String.format(
                    "%s/simple/price?vs_currencies=%s&ids=%s&x_cg_demo_api_key=%s",
                    baseUrl,
                    vsCurrency.toLowerCase(),
                    cryptoId,
                    apiKey
            );

            String response = restTemplate.getForObject(url, String.class);

            if (response == null || response.isEmpty()) {
                log.warn("No data returned for crypto: {}", symbol);
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode cryptoNode = root.get(cryptoId);

            if (cryptoNode == null) {
                log.warn("Crypto node not found in response for: {}", symbol);
                return null;
            }

            JsonNode priceNode = cryptoNode.get(vsCurrency.toLowerCase());

            if (priceNode == null || !priceNode.isNumber()) {
                log.warn("Price not found for {} in {}", symbol, vsCurrency);
                return null;
            }

            BigDecimal price = new BigDecimal(priceNode.asText());
            log.info("Successfully retrieved price for {}: {}", symbol, price);
            return price;

        } catch (Exception e) {
            log.error("Error fetching price for crypto {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Récupère le prix d'une crypto avec devise par défaut USD
     */
    public BigDecimal getCryptoPrice(String symbol) {
        return getCryptoPrice(symbol, "usd");
    }

    /**
     * Vérifie si un symbole est supporté
     */
    public boolean isCryptoSupported(String symbol) {
        return CRYPTO_ID_MAP.containsKey(symbol.toUpperCase());
    }

    /**
     * Récupère tous les symboles supportés
     */
    public Map<String, String> getSupportedCryptos() {
        return new HashMap<>(CRYPTO_ID_MAP);
    }
}