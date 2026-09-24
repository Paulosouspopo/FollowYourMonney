-- =============================================================================
-- V1 — Schéma initial (état des entités JPA au 2026-09-24).
--
-- Généré depuis Hibernate sur une base vierge puis relu : c'est désormais
-- Flyway, et non plus `ddl-auto`, qui fait évoluer le schéma. Toute
-- modification d'entité passe par une nouvelle migration V2, V3...
-- (Hibernate est en `validate` et refuse de démarrer si les deux divergent).
--
-- Une base existante créée avant Flyway est « baselinée » en V1
-- (spring.flyway.baseline-version=1) : ce script n'y est pas rejoué.
-- =============================================================================

-- ------------------------------------------------------------------ users
CREATE TABLE users (
    id                 uuid         NOT NULL,
    email              varchar(255) NOT NULL,
    password           varchar(255) NOT NULL,
    username           varchar(255) NOT NULL,
    preferred_currency varchar(255),
    created_at         timestamp(6),
    updated_at         timestamp(6),
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email)
);

-- ------------------------------------------------------------- portfolios
CREATE TABLE portfolios (
    id          uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    name        varchar(255) NOT NULL,
    description varchar(255),
    type        varchar(255) NOT NULL,
    created_at  timestamp(6),
    updated_at  timestamp(6),
    CONSTRAINT portfolios_pkey PRIMARY KEY (id),
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT portfolios_type_check
        CHECK (type IN ('PEA', 'CTO', 'CRYPTO', 'LIVRET', 'IMMOBILIER', 'AUTRE'))
);

-- ----------------------------------------------------------------- assets
CREATE TABLE assets (
    id            uuid         NOT NULL,
    portfolio_id  uuid         NOT NULL,
    symbol        varchar(255) NOT NULL,
    name          varchar(255) NOT NULL,
    long_name     varchar(255),
    exchange_name varchar(255),
    asset_type    varchar(255),
    currency      varchar(255),
    created_at    timestamp(6),
    updated_at    timestamp(6),
    CONSTRAINT assets_pkey PRIMARY KEY (id),
    CONSTRAINT fk_assets_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id),
    CONSTRAINT assets_asset_type_check
        CHECK (asset_type IN ('ACTION', 'ETF', 'CRYPTO', 'LIVRET', 'IMMOBILIER', 'AUTRE'))
);

-- ----------------------------------------------------------- transactions
CREATE TABLE transactions (
    id                   uuid           NOT NULL,
    asset_id             uuid           NOT NULL,
    type                 varchar(20)    NOT NULL,
    quantity             numeric(19, 8) NOT NULL,
    price_per_unit       numeric(19, 8) NOT NULL,
    fees                 numeric(19, 2) NOT NULL,
    total_amount         numeric(19, 2) NOT NULL,
    currency             varchar(3)     NOT NULL,
    exchange_rate_to_eur numeric(19, 8) NOT NULL,
    total_amount_eur     numeric(19, 2) NOT NULL,
    fees_eur             numeric(19, 2) NOT NULL,
    transaction_date     timestamp(6)   NOT NULL,
    notes                varchar(500),
    created_at           timestamp(6),
    updated_at           timestamp(6),
    CONSTRAINT transactions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_transactions_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT transactions_type_check CHECK (type IN ('BUY', 'SELL', 'DIVIDEND'))
);
CREATE INDEX idx_transactions_asset_date ON transactions (asset_id, transaction_date);

-- -------------------------------------------------- recurring_investments
CREATE TABLE recurring_investments (
    id             uuid           NOT NULL,
    user_id        uuid           NOT NULL,
    asset_id       uuid           NOT NULL,
    amount         numeric(19, 2) NOT NULL,
    frequency      varchar(255)   NOT NULL,
    start_date     timestamp(6),
    next_execution timestamp(6),
    end_date       timestamp(6),
    active         boolean,
    created_at     timestamp(6),
    updated_at     timestamp(6),
    CONSTRAINT recurring_investments_pkey PRIMARY KEY (id),
    CONSTRAINT fk_recurring_investments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_recurring_investments_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT recurring_investments_frequency_check
        CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'))
);

-- ------------------------------------------------------ données de marché
-- Une ligne par (symbole, jour de bourse) ; sert aussi aux paires FX (USDEUR=X).
CREATE TABLE asset_prices (
    id           uuid           NOT NULL,
    symbol       varchar(255)   NOT NULL,
    price_date   date           NOT NULL,
    price        numeric(19, 8) NOT NULL,
    currency     varchar(255),
    source       varchar(255),
    last_updated timestamp(6),
    CONSTRAINT asset_prices_pkey PRIMARY KEY (id),
    CONSTRAINT uk_asset_prices_symbol_price_date UNIQUE (symbol, price_date)
);
CREATE INDEX idx_asset_prices_symbol_price_date ON asset_prices (symbol, price_date);

-- Intervalle de jours déjà demandé à Yahoo, par symbole.
CREATE TABLE price_history_coverage (
    symbol       varchar(64) NOT NULL,
    covered_from date        NOT NULL,
    covered_to   date        NOT NULL,
    updated_at   timestamp(6),
    CONSTRAINT price_history_coverage_pkey PRIMARY KEY (symbol)
);

CREATE TABLE exchange_rates (
    id            uuid           NOT NULL,
    from_currency varchar(255)   NOT NULL,
    to_currency   varchar(255)   NOT NULL,
    rate          numeric(19, 8) NOT NULL,
    source        varchar(255),
    last_updated  timestamp(6),
    CONSTRAINT exchange_rates_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_exchange_rates_pair_last_updated ON exchange_rates (from_currency, to_currency, last_updated);

-- ---------------------------------------------------- portfolio_snapshots
CREATE TABLE portfolio_snapshots (
    id                   uuid           NOT NULL,
    portfolio_id         uuid           NOT NULL,
    snapshot_date        date           NOT NULL,
    total_value          numeric(19, 2) NOT NULL,
    total_invested       numeric(19, 2) NOT NULL,
    gain_loss            numeric(19, 2) NOT NULL,
    gain_loss_percentage numeric(19, 4) NOT NULL,
    base_currency        varchar(3)     NOT NULL,
    created_at           timestamp(6),
    CONSTRAINT portfolio_snapshots_pkey PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_snapshots_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id),
    CONSTRAINT uk_snapshot_portfolio_date UNIQUE (portfolio_id, snapshot_date)
);
CREATE INDEX idx_snapshot_portfolio_date ON portfolio_snapshots (portfolio_id, snapshot_date);
