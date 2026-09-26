-- =============================================================================
-- V16 — Mode démo : comptes invités avec un patrimoine fictif, effacés après 24 h.
-- =============================================================================
ALTER TABLE users ADD COLUMN demo boolean NOT NULL DEFAULT false;
CREATE INDEX idx_users_demo ON users (demo, created_at) WHERE demo;
