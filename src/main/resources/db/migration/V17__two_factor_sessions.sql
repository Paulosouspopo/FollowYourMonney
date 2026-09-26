-- =============================================================================
-- V17 — Double authentification (TOTP) et sessions actives par appareil.
-- =============================================================================

-- Sessions : appareil (navigateur, IP), début de la session (conservé à chaque
-- rotation du jeton) et dernière utilisation
ALTER TABLE refresh_tokens ADD COLUMN session_started_at timestamp(6);
ALTER TABLE refresh_tokens ADD COLUMN last_used_at timestamp(6);
ALTER TABLE refresh_tokens ADD COLUMN user_agent varchar(255);
ALTER TABLE refresh_tokens ADD COLUMN ip varchar(64);
UPDATE refresh_tokens SET session_started_at = created_at, last_used_at = created_at;

-- TOTP (RFC 6238) : secret chiffré (AES-GCM), secret en attente pendant
-- l'activation, dernier pas de temps accepté (pas de rejeu d'un même code)
ALTER TABLE users ADD COLUMN totp_secret varchar(255);
ALTER TABLE users ADD COLUMN totp_pending_secret varchar(255);
ALTER TABLE users ADD COLUMN totp_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN totp_last_step bigint;

-- Codes de secours : à usage unique, seule leur empreinte est stockée
CREATE TABLE totp_recovery_codes (
    id        uuid         NOT NULL,
    user_id   uuid         NOT NULL,
    code_hash varchar(64)  NOT NULL,
    used_at   timestamp(6),
    CONSTRAINT totp_recovery_codes_pkey PRIMARY KEY (id),
    CONSTRAINT fk_totp_recovery_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_totp_recovery_codes_user ON totp_recovery_codes (user_id);

-- Défi de connexion après le mot de passe (5 minutes)
ALTER TABLE account_tokens DROP CONSTRAINT account_tokens_type_check;
ALTER TABLE account_tokens ADD CONSTRAINT account_tokens_type_check
    CHECK (type IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'TWO_FACTOR'));
