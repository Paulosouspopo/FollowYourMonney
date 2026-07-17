package com.portfolio.tracker.asset.external;

import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service
public class AssetExternalService {

    /**
     * Récupère tous les assets disponibles
     */
    public List<AssetTemplate> getAvailableAssets() {
        return Arrays.asList(AssetTemplate.values());
    }

    /**
     * Récupère les données d'un asset depuis le template
     */
    public AssetTemplate getAssetData(String symbol) {
        return AssetTemplate.fromSymbol(symbol);
    }
}