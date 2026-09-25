-- =============================================================================
-- V15 — Corbeille : opérations, mouvements et portefeuilles supprimés,
-- restaurables pendant 30 jours (instantané JSON, purgé ensuite).
-- =============================================================================
CREATE TABLE trash_items (
    id           uuid          NOT NULL,
    user_id      uuid          NOT NULL,
    kind         varchar(20)   NOT NULL,
    portfolio_id uuid,
    label        varchar(255)  NOT NULL,
    payload      text          NOT NULL,
    deleted_at   timestamp(6)  NOT NULL,
    CONSTRAINT trash_items_pkey PRIMARY KEY (id),
    CONSTRAINT fk_trash_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT trash_items_kind_check CHECK (kind IN ('TRANSACTION', 'CASH_MOVEMENT', 'PORTFOLIO'))
);
CREATE INDEX idx_trash_items_user ON trash_items (user_id, deleted_at);
