package com.portfolio.tracker.notification.alert;

import com.portfolio.tracker.notification.Formats;

/** Règle en français : « Patrimoine total baisse de 3 % sur 1 jour ». */
public final class AlertRuleDescriber {

    private AlertRuleDescriber() {
    }

    public static String subject(AlertRule r) {
        return switch (r.getScope()) {
            case GLOBAL -> "Patrimoine total";
            case PORTFOLIO -> r.getPortfolio() != null ? r.getPortfolio().getName() : "Portefeuille";
            case ASSET -> r.getAssetName() != null ? r.getAssetName() + " (" + r.getSymbol() + ")" : r.getSymbol();
        };
    }

    public static String describe(AlertRule r) {
        String what = switch (r.getCondition()) {
            case RISES -> "monte de " + Formats.percent(r.getThreshold());
            case FALLS -> "baisse de " + Formats.percent(r.getThreshold());
            case MOVES -> "varie de " + Formats.percent(r.getThreshold());
            case ABOVE -> "passe au-dessus de " + Formats.eur(r.getThreshold());
            case BELOW -> "passe en dessous de " + Formats.eur(r.getThreshold());
        };
        return subject(r) + " " + what + (r.getCondition().isPercentage() ? " " + period(r.getPeriod()) : "");
    }

    public static String period(AlertRule.Period p) {
        return switch (p) {
            case DAY -> "sur 1 jour";
            case WEEK -> "sur 7 jours";
            case MONTH -> "sur 30 jours";
        };
    }
}
