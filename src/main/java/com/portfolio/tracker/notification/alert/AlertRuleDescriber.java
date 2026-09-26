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
            case ASSET -> r.getAssetName() != null ? r.getAssetName() : r.getSymbol();
        };
    }

    public static String describe(AlertRule r) {
        String what = switch (r.getCondition()) {
            case RISES -> "monte de " + Formats.percent(r.getThreshold());
            case FALLS -> "baisse de " + Formats.percent(r.getThreshold());
            case MOVES -> "varie de " + Formats.percent(r.getThreshold());
            case ABOVE -> "passe au-dessus de " + Formats.eur(r.getThreshold());
            case BELOW -> "passe en dessous de " + Formats.eur(r.getThreshold());
            case PROFIT_ABOVE -> "dépasse " + Formats.percent(r.getThreshold()) + " de plus-value latente";
            case LOSS_BELOW -> "dépasse " + Formats.percent(r.getThreshold()) + " de moins-value latente";
            case NEW_HIGH -> "atteint son plus haut";
            case NEW_LOW -> "atteint son plus bas";
            case WEIGHT_ABOVE -> "dépasse " + Formats.percent(r.getThreshold()) + " du patrimoine";
        };
        return subject(r) + " " + what + (r.getCondition().usesPeriod() ? " " + period(r.getPeriod()) : "");
    }

    public static String period(AlertRule.Period p) {
        return switch (p) {
            case DAY -> "sur 1 jour";
            case WEEK -> "sur 7 jours";
            case MONTH -> "sur 30 jours";
            case YEAR -> "sur 1 an";
        };
    }
}
