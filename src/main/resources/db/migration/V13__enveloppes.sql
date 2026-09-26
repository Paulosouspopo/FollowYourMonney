-- =============================================================================
-- V13 — Enveloppes (assurance-vie, PER, épargne salariale), fonds, actifs non
-- cotés, liquidités en devises, abondement, tranche marginale d'imposition.
-- =============================================================================

-- Nouveaux types de portefeuille : liquidités toujours suivies (fonds euros,
-- sommes en attente d'investissement)
ALTER TABLE portfolios DROP CONSTRAINT portfolios_type_check;
ALTER TABLE portfolios ADD CONSTRAINT portfolios_type_check
    CHECK (type IN ('PEA', 'CTO', 'CRYPTO', 'LIVRET', 'ASSURANCE_VIE', 'PER', 'EPARGNE_SALARIALE', 'IMMOBILIER', 'AUTRE'));

-- Compte multidevise : les opérations sont réglées dans leur devise (solde
-- USD, GBP…) au lieu d'être converties en euros
ALTER TABLE portfolios ADD COLUMN multi_currency_cash boolean NOT NULL DEFAULT false;

-- Fonds (OPCVM, FCPE, unités de compte)
ALTER TABLE assets DROP CONSTRAINT assets_asset_type_check;
ALTER TABLE assets ADD CONSTRAINT assets_asset_type_check
    CHECK (asset_type IN ('ACTION', 'ETF', 'FONDS', 'CRYPTO', 'LIVRET', 'IMMOBILIER', 'AUTRE'));

-- Actif non coté : valeur saisie par l'utilisateur (symbole interne « ~… »,
-- jamais envoyé à Yahoo ; ses valeurs sont des lignes d'asset_prices)
ALTER TABLE assets ADD COLUMN manual boolean NOT NULL DEFAULT false;

-- Mouvements : devise (EUR par défaut), taux du jour, abondement, change
ALTER TABLE cash_movements DROP CONSTRAINT cash_movements_type_check;
ALTER TABLE cash_movements ADD CONSTRAINT cash_movements_type_check
    CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'INTEREST', 'FEE', 'ABONDEMENT', 'CONVERSION'));
ALTER TABLE cash_movements ADD COLUMN currency varchar(3) NOT NULL DEFAULT 'EUR';
ALTER TABLE cash_movements ADD COLUMN exchange_rate_to_eur numeric(19, 8) NOT NULL DEFAULT 1;
-- Change (CONVERSION) : `amount` quitte `currency`, `counter_amount` arrive en `counter_currency`
ALTER TABLE cash_movements ADD COLUMN counter_amount numeric(19, 2);
ALTER TABLE cash_movements ADD COLUMN counter_currency varchar(3);
ALTER TABLE cash_movements ADD CONSTRAINT cash_movements_conversion_check CHECK (
    (type = 'CONVERSION' AND counter_amount > 0 AND counter_currency IS NOT NULL AND counter_currency <> currency)
    OR (type <> 'CONVERSION' AND counter_amount IS NULL AND counter_currency IS NULL));

-- Tranche marginale d'imposition (%), pour estimer l'avantage fiscal du PER
ALTER TABLE users ADD COLUMN marginal_tax_rate integer NOT NULL DEFAULT 30;
ALTER TABLE users ADD CONSTRAINT users_marginal_tax_rate_check CHECK (marginal_tax_rate IN (0, 11, 30, 41, 45));
