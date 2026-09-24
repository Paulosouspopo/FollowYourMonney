-- =============================================================================
-- V8 — Seuil nul autorisé : les alertes « nouveau plus haut / plus bas » n'en ont pas.
-- =============================================================================
ALTER TABLE alert_rules DROP CONSTRAINT alert_rules_threshold_check;
ALTER TABLE alert_rules ADD CONSTRAINT alert_rules_threshold_check CHECK (threshold >= 0);
