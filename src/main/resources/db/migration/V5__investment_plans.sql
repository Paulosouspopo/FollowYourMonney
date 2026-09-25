-- =============================================================================
-- V5 — Investissements programmés (plans d'investissement, versements
-- automatiques). Remplace l'ébauche recurring_investments, jamais utilisée.
-- =============================================================================

DROP TABLE recurring_investments;

CREATE TABLE investment_plans (
    id                  uuid           NOT NULL,
    portfolio_id        uuid           NOT NULL,
    -- BUY : achat d'un actif ; DEPOSIT : versement d'espèces
    type                varchar(20)    NOT NULL,
    -- Symbole Yahoo (BUY uniquement)
    symbol              varchar(64),
    name                varchar(255)   NOT NULL,
    -- Montant de chaque échéance, en EUR, frais compris
    amount              numeric(19, 2) NOT NULL,
    fees                numeric(19, 2) NOT NULL DEFAULT 0,
    frequency           varchar(20)    NOT NULL,
    start_date          date           NOT NULL,
    end_date            date,
    -- false : parts entières uniquement (PEA classique), le reste n'est pas investi
    fractional          boolean        NOT NULL DEFAULT true,
    active              boolean        NOT NULL DEFAULT true,
    -- Nombre d'échéances déjà traitées (exécutées ou sautées) depuis start_date
    occurrences         integer        NOT NULL DEFAULT 0,
    next_execution_date date,
    last_execution_date date,
    last_error          varchar(500),
    created_at          timestamp(6),
    updated_at          timestamp(6),
    CONSTRAINT investment_plans_pkey PRIMARY KEY (id),
    CONSTRAINT fk_investment_plans_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE,
    CONSTRAINT investment_plans_type_check CHECK (type IN ('BUY', 'DEPOSIT')),
    CONSTRAINT investment_plans_frequency_check
        CHECK (frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT investment_plans_amount_check CHECK (amount > 0 AND fees >= 0 AND fees < amount),
    CONSTRAINT investment_plans_symbol_check CHECK (type = 'DEPOSIT' OR symbol IS NOT NULL)
);
CREATE INDEX idx_investment_plans_due ON investment_plans (active, next_execution_date);
