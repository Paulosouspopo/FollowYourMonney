-- =============================================================================
-- V10 — Contrôles de cohérence ignorés par l'utilisateur ; objectifs d'épargne.
-- =============================================================================

-- « C'est normal » : un contrôle ignoré ne réapparaît plus (clé stable du contrôle)
CREATE TABLE data_check_dismissals (
    user_id    uuid         NOT NULL,
    issue_key  varchar(120) NOT NULL,
    created_at timestamp(6),
    CONSTRAINT data_check_dismissals_pkey PRIMARY KEY (user_id, issue_key),
    CONSTRAINT fk_data_check_dismissals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Objectifs : un montant à atteindre (tout le patrimoine ou un portefeuille), avec une date facultative
CREATE TABLE goals (
    id            uuid          NOT NULL,
    user_id       uuid          NOT NULL,
    name          varchar(100)  NOT NULL,
    target_amount numeric(19, 2) NOT NULL,
    target_date   date,
    portfolio_id  uuid,
    created_at    timestamp(6),
    updated_at    timestamp(6),
    CONSTRAINT goals_pkey PRIMARY KEY (id),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_goals_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE,
    CONSTRAINT goals_target_check CHECK (target_amount > 0)
);
CREATE INDEX idx_goals_user ON goals (user_id);
