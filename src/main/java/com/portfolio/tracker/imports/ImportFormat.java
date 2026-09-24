package com.portfolio.tracker.imports;

/** Formats de relevés reconnus. GENERIC = colonnes associées par l'utilisateur. */
public enum ImportFormat {
    FORTUNEO("Fortuneo"),
    TRADE_REPUBLIC("Trade Republic"),
    BINANCE("Binance"),
    GENERIC("Autre (colonnes à associer)");

    private final String label;

    ImportFormat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
