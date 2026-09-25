-- =============================================================================
-- V2 — Sécurité des comptes : rôles, vérification d'email, sessions
-- (jetons de renouvellement) et jetons à usage unique (email, mot de passe).
-- =============================================================================

-- Rôle stocké en base (remplace la liste d'emails en configuration, qui ne
-- sert plus qu'à promouvoir les premiers administrateurs au démarrage).
ALTER TABLE users ADD COLUMN role varchar(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));

-- Les comptes créés avant cette version sont considérés comme vérifiés.
ALTER TABLE users ADD COLUMN email_verified boolean NOT NULL DEFAULT false;
UPDATE users SET email_verified = true;

-- Sessions : un jeton de renouvellement par appareil connecté. Seul son
-- empreinte SHA-256 est stockée ; une ligne révoquée est conservée jusqu'à
-- expiration pour détecter la réutilisation d'un jeton volé.
CREATE TABLE refresh_tokens (
    id         uuid         NOT NULL,
    user_id    uuid         NOT NULL,
    token_hash varchar(64)  NOT NULL,
    expires_at timestamp(6) NOT NULL,
    revoked_at timestamp(6),
    created_at timestamp(6),
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- Jetons à usage unique envoyés par email (vérification, réinitialisation).
CREATE TABLE account_tokens (
    id         uuid         NOT NULL,
    user_id    uuid         NOT NULL,
    type       varchar(30)  NOT NULL,
    token_hash varchar(64)  NOT NULL,
    expires_at timestamp(6) NOT NULL,
    used_at    timestamp(6),
    created_at timestamp(6),
    CONSTRAINT account_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT uk_account_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT account_tokens_type_check CHECK (type IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);
CREATE INDEX idx_account_tokens_user_type ON account_tokens (user_id, type);

-- Suppression de compte (RGPD) : les snapshots et investissements programmés
-- suivent la suppression de leur parent au lieu de la bloquer.
ALTER TABLE portfolio_snapshots DROP CONSTRAINT fk_portfolio_snapshots_portfolio;
ALTER TABLE portfolio_snapshots ADD CONSTRAINT fk_portfolio_snapshots_portfolio
    FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE;
ALTER TABLE recurring_investments DROP CONSTRAINT fk_recurring_investments_user;
ALTER TABLE recurring_investments ADD CONSTRAINT fk_recurring_investments_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
