-- =============================================================================
-- V18 — « Investi » = apports nets (versements - retraits + découvert, ou
-- achats - ventes - dividendes sans suivi des liquidités) et valeur sans le
-- découvert. Les snapshots existants suivent l'ancienne règle : on efface leurs
-- flux pour que le rattrapage du démarrage les recalcule entièrement.
-- =============================================================================
UPDATE portfolio_snapshots SET net_flow = NULL;
