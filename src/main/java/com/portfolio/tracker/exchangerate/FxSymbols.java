package com.portfolio.tracker.exchangerate;

/** Symboles Yahoo des paires de devises : 1 {@code from} = x {@code to}. */
public final class FxSymbols {

    private FxSymbols() {
    }

    public static String pair(String from, String to) {
        return from.toUpperCase() + to.toUpperCase() + "=X";
    }
}
