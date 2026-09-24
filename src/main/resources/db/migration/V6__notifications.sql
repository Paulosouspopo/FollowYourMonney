-- =============================================================================
-- V6 — Notifications : règles d'alerte personnalisées, boîte de réception,
-- rapport périodique.
-- =============================================================================

-- « Quand <périmètre> <condition> <seuil> <période> → prévenir par <canaux> »
CREATE TABLE alert_rules (
    id                uuid           NOT NULL,
    user_id           uuid           NOT NULL,
    -- GLOBAL : patrimoine total ; PORTFOLIO : un portefeuille ; ASSET : un actif (détenu ou non)
    scope             varchar(20)    NOT NULL,
    portfolio_id      uuid,
    symbol            varchar(64),
    asset_name        varchar(255),
    -- RISES / FALLS / MOVES (variation en %) ; ABOVE / BELOW (seuil en EUR)
    condition_type    varchar(20)    NOT NULL,
    threshold         numeric(19, 4) NOT NULL,
    -- Période de la variation : DAY (depuis la veille), WEEK (7 j), MONTH (30 j)
    period            varchar(20),
    notify_email      boolean        NOT NULL DEFAULT false,
    enabled           boolean        NOT NULL DEFAULT true,
    -- false après un déclenchement, jusqu'à ce que la condition redevienne fausse
    armed             boolean        NOT NULL DEFAULT true,
    last_triggered_at timestamp(6),
    created_at        timestamp(6),
    updated_at        timestamp(6),
    CONSTRAINT alert_rules_pkey PRIMARY KEY (id),
    CONSTRAINT fk_alert_rules_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_rules_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE,
    CONSTRAINT alert_rules_scope_check CHECK (scope IN ('GLOBAL', 'PORTFOLIO', 'ASSET')),
    CONSTRAINT alert_rules_condition_check CHECK (condition_type IN ('RISES', 'FALLS', 'MOVES', 'ABOVE', 'BELOW')),
    CONSTRAINT alert_rules_period_check CHECK (period IS NULL OR period IN ('DAY', 'WEEK', 'MONTH')),
    CONSTRAINT alert_rules_target_check CHECK (
        (scope = 'GLOBAL') OR (scope = 'PORTFOLIO' AND portfolio_id IS NOT NULL) OR (scope = 'ASSET' AND symbol IS NOT NULL)),
    CONSTRAINT alert_rules_threshold_check CHECK (threshold > 0)
);
CREATE INDEX idx_alert_rules_user ON alert_rules (user_id);

-- Boîte de réception de l'application
CREATE TABLE notifications (
    id         uuid          NOT NULL,
    user_id    uuid          NOT NULL,
    -- ALERT, REPORT, PLAN
    type       varchar(20)   NOT NULL,
    title      varchar(200)  NOT NULL,
    body       varchar(4000) NOT NULL,
    -- Chemin de l'app à ouvrir (ex : /portfolios/<id>)
    link       varchar(255),
    read_at    timestamp(6),
    created_at timestamp(6)  NOT NULL,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT notifications_type_check CHECK (type IN ('ALERT', 'REPORT', 'PLAN'))
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);

-- Rapport périodique (un par utilisateur)
CREATE TABLE report_settings (
    user_id        uuid        NOT NULL,
    -- NONE, DAILY, WEEKLY (le lundi)
    frequency      varchar(20) NOT NULL DEFAULT 'NONE',
    -- Heure d'envoi, fuseau de Paris
    send_hour      integer     NOT NULL DEFAULT 19,
    notify_email   boolean     NOT NULL DEFAULT true,
    last_sent_date date,
    CONSTRAINT report_settings_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_report_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT report_settings_frequency_check CHECK (frequency IN ('NONE', 'DAILY', 'WEEKLY')),
    CONSTRAINT report_settings_hour_check CHECK (send_hour BETWEEN 0 AND 23)
);
