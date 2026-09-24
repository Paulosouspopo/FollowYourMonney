package com.portfolio.tracker.marketdata;

/**
 * Le provider n'a pas pu répondre (réseau, 4xx/5xx, réponse illisible).
 *
 * À distinguer d'une réponse vide : un historique vide est une réponse
 * valide (actif pas encore coté à ces dates) et peut être considéré comme
 * "couvert", alors qu'une erreur doit être retentée plus tard.
 */
public class MarketDataUnavailableException extends RuntimeException {

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
