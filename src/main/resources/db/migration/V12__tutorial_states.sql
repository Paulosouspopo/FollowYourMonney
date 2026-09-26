-- =============================================================================
-- V12 — Tutoriels : visites guidées terminées et affichage automatique, par compte
-- (un tutoriel vu sur le téléphone n'est pas remontré sur l'ordinateur).
-- =============================================================================
CREATE TABLE tutorial_states (
    user_id      uuid          NOT NULL,
    auto_enabled boolean       NOT NULL DEFAULT true,
    -- Clés des visites terminées ou ignorées, séparées par des virgules
    completed    varchar(2000) NOT NULL DEFAULT '',
    updated_at   timestamp(6),
    CONSTRAINT tutorial_states_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_tutorial_states_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
