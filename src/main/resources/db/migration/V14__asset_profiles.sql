-- =============================================================================
-- V14 — Radiographie : profil de marché des actifs (pays, secteurs, lignes des
-- fonds, frais) mis en cache, et frais annuels saisis par l'utilisateur.
-- =============================================================================

-- Une ligne par symbole Yahoo, rafraîchie tous les 30 jours (1 jour après un échec)
CREATE TABLE asset_profiles (
    symbol            varchar(255)  NOT NULL,
    quote_type        varchar(30),
    long_name         varchar(255),
    country           varchar(100),
    sector            varchar(100),
    -- JSON : {"technology": 0.3062, ...} (parts de 0 à 1)
    sector_weights    text,
    -- JSON : [{"symbol": "AAPL", "name": "Apple Inc", "weight": 0.052}, ...] (10 premières lignes)
    holdings          text,
    stock_pct         numeric(7, 4),
    bond_pct          numeric(7, 4),
    cash_pct          numeric(7, 4),
    other_pct         numeric(7, 4),
    -- Frais courants annuels (TER), en % (0.38 = 0,38 %)
    expense_ratio_pct numeric(6, 3),
    fetched_at        timestamp(6)  NOT NULL,
    ok                boolean       NOT NULL,
    CONSTRAINT asset_profiles_pkey PRIMARY KEY (symbol)
);

-- Frais courants annuels d'une ligne renseignés par l'utilisateur (Yahoo ne les
-- connaît pas toujours), en %
ALTER TABLE assets ADD COLUMN annual_fee_pct numeric(6, 3);
ALTER TABLE assets ADD CONSTRAINT assets_annual_fee_pct_check CHECK (annual_fee_pct IS NULL OR (annual_fee_pct >= 0 AND annual_fee_pct <= 10));
