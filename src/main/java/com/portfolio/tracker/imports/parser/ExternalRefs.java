package com.portfolio.tracker.imports.parser;

import com.portfolio.tracker.shared.security.SecureTokens;

import java.util.HashMap;
import java.util.Map;

/**
 * Références anti-doublon pour les relevés sans identifiant d'opération
 * (Fortuneo, Binance, import générique) : empreinte du contenu de la ligne.
 *
 * Deux exports qui se chevauchent contiennent les mêmes lignes, donc les
 * mêmes empreintes. Deux lignes strictement identiques dans un même fichier
 * (deux achats identiques le même jour) sont distinguées par leur rang.
 */
final class ExternalRefs {

    private final String prefix;
    private final Map<String, Integer> occurrences = new HashMap<>();

    ExternalRefs(String prefix) {
        this.prefix = prefix;
    }

    String of(String content) {
        String hash = SecureTokens.hash(content.trim()).substring(0, 32);
        int rank = occurrences.merge(hash, 1, Integer::sum);
        return prefix + ":" + hash + (rank > 1 ? "#" + rank : "");
    }
}
