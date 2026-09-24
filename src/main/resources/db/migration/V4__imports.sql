-- =============================================================================
-- V4 — Import de relevés (CSV de courtiers).
-- =============================================================================

-- Référence de l'opération dans le relevé d'origine (identifiant du courtier,
-- ou empreinte de la ligne) : un second import du même relevé ne crée pas de
-- doublon. NULL pour une saisie manuelle.
ALTER TABLE transactions ADD COLUMN external_ref varchar(100);
CREATE INDEX idx_transactions_external_ref ON transactions (external_ref);

ALTER TABLE cash_movements ADD COLUMN external_ref varchar(100);
CREATE INDEX idx_cash_movements_external_ref ON cash_movements (external_ref);

-- Correspondances mémorisées « actif du relevé → symbole Yahoo » validées par
-- l'utilisateur (ex : NAME:BNP PARIBAS EASY S&P 500 UCITS ETF -> ESE.PA).
CREATE TABLE import_asset_mappings (
    id         uuid         NOT NULL,
    user_id    uuid         NOT NULL,
    reference  varchar(255) NOT NULL,
    symbol     varchar(64)  NOT NULL,
    created_at timestamp(6),
    updated_at timestamp(6),
    CONSTRAINT import_asset_mappings_pkey PRIMARY KEY (id),
    CONSTRAINT uk_import_asset_mappings_user_reference UNIQUE (user_id, reference),
    CONSTRAINT fk_import_asset_mappings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
