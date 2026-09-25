-- =============================================================================
-- V11 — Date d'ouverture du compte (fiscalité : les 5 ans d'un PEA).
-- Facultative : à défaut, la date de la première opération est utilisée (estimée).
-- =============================================================================
ALTER TABLE portfolios ADD COLUMN opened_at date;
