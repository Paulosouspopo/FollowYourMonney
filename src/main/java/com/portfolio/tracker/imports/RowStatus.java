package com.portfolio.tracker.imports;

/** État d'une ligne dans l'aperçu d'import. */
public enum RowStatus {
    /** Prête à être importée (sélectionnée par défaut). */
    READY,
    /** Déjà présente dans le portefeuille (non sélectionnée par défaut). */
    DUPLICATE,
    /** Lue mais volontairement non importée (raison dans le message). */
    IGNORED,
    /** Importable seulement après correction (raison dans le message). */
    ERROR
}
