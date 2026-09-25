-- =============================================================================
-- V3 — Mouvements d'argent (versements, retraits, intérêts, frais de compte)
-- et suivi des liquidités par portefeuille. Un livret est un portefeuille dont
-- la valeur est uniquement son solde.
-- =============================================================================

-- Suivi des liquidités : le solde (versements - retraits + intérêts - frais
-- +/- flux des opérations) entre dans la valeur du portefeuille. Toujours
-- actif pour un livret, optionnel ailleurs.
ALTER TABLE portfolios ADD COLUMN cash_tracking boolean NOT NULL DEFAULT false;
UPDATE portfolios SET cash_tracking = true WHERE type = 'LIVRET';

-- Taux annuel affiché pour un livret (en %, ex : 2.400). Informatif : les
-- intérêts réellement versés sont saisis comme mouvements.
ALTER TABLE portfolios ADD COLUMN annual_interest_rate numeric(6, 3);

-- Montants en EUR, toujours positifs : le type donne le sens du flux.
CREATE TABLE cash_movements (
    id            uuid           NOT NULL,
    portfolio_id  uuid           NOT NULL,
    type          varchar(20)    NOT NULL,
    amount        numeric(19, 2) NOT NULL,
    movement_date date           NOT NULL,
    notes         varchar(500),
    created_at    timestamp(6),
    updated_at    timestamp(6),
    CONSTRAINT cash_movements_pkey PRIMARY KEY (id),
    CONSTRAINT fk_cash_movements_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE,
    CONSTRAINT cash_movements_type_check CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'INTEREST', 'FEE')),
    CONSTRAINT cash_movements_amount_check CHECK (amount > 0)
);
CREATE INDEX idx_cash_movements_portfolio_date ON cash_movements (portfolio_id, movement_date);
