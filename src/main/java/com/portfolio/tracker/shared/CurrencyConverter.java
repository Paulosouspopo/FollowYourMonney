package com.portfolio.tracker.shared;

import com.portfolio.tracker.exchangerate.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Convertit des montants vers/depuis la devise pivot (EUR).
 *
 * Utilisation : ouvrir une session au début d'un traitement (dashboard,
 * valorisation) pour mutualiser les taux. Chaque paire de devises n'est
 * résolue qu'une seule fois, quel que soit le nombre de conversions.
 */
@Component
@RequiredArgsConstructor
public class CurrencyConverter {

    private final ExchangeRateService exchangeRateService;

    public Session openSession() {
        return new Session();
    }

    /**
     * Cache de taux à durée de vie limitée à un traitement.
     * NON thread-safe par conception : une session appartient à un seul thread.
     */
    public class Session {

        private final Map<String, BigDecimal> cache = new HashMap<>();

        /** Convertit un montant depuis {@code from} vers EUR. */
        public BigDecimal toEur(BigDecimal amount, String from) {
            if (amount == null || amount.signum() == 0) {
                return BigDecimal.ZERO;
            }
            return amount.multiply(rate(from, MoneyConstants.BASE_CURRENCY));
        }

        /** Convertit un montant depuis EUR vers {@code to}. */
        public BigDecimal fromEur(BigDecimal amountEur, String to) {
            if (amountEur == null || amountEur.signum() == 0) {
                return BigDecimal.ZERO;
            }
            return amountEur.multiply(rate(MoneyConstants.BASE_CURRENCY, to))
                    .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
        }

        /** Taux mis en cache pour la durée de la session. */
        public BigDecimal rate(String from, String to) {
            if (from == null || to == null || from.equalsIgnoreCase(to)) {
                return BigDecimal.ONE;
            }
            String key = from.toUpperCase() + '>' + to.toUpperCase();
            return cache.computeIfAbsent(key,
                    k -> exchangeRateService.getRate(from.toUpperCase(), to.toUpperCase()));
        }
    }
}