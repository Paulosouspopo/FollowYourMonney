-- =============================================================================
-- V9 — Flux externes par jour : base du calcul de performance (TWR, XIRR).
-- NULL = snapshot antérieur à cette migration : le rattrapage du démarrage
-- recalcule entièrement les portefeuilles concernés.
-- =============================================================================

-- Argent entré (+) ou sorti (-) du portefeuille ce jour-là, en EUR
ALTER TABLE portfolio_snapshots ADD COLUMN net_flow numeric(19, 2);

-- Valeur retenue pour la performance : valeur totale, un solde de liquidités
-- négatif (versements non saisis) étant compté comme un apport, pas une perte
ALTER TABLE portfolio_snapshots ADD COLUMN performance_value numeric(19, 2);
