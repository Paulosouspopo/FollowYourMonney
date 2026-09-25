-- =============================================================================
-- V7 — Actifs suivis (watchlist), notifications push, alertes enrichies.
-- =============================================================================

-- Actifs suivis, détenus ou non (cours mis à jour chaque heure comme les actifs détenus)
CREATE TABLE watchlist_items (
    id         uuid         NOT NULL,
    user_id    uuid         NOT NULL,
    symbol     varchar(64)  NOT NULL,
    name       varchar(255) NOT NULL,
    asset_type varchar(20),
    created_at timestamp(6),
    CONSTRAINT watchlist_items_pkey PRIMARY KEY (id),
    CONSTRAINT uk_watchlist_user_symbol UNIQUE (user_id, symbol),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Abonnements Web Push (un par navigateur / appareil)
CREATE TABLE push_subscriptions (
    id              uuid          NOT NULL,
    user_id         uuid          NOT NULL,
    endpoint        varchar(1000) NOT NULL,
    p256dh          varchar(200)  NOT NULL,
    auth            varchar(100)  NOT NULL,
    device_label    varchar(200),
    created_at      timestamp(6),
    last_success_at timestamp(6),
    CONSTRAINT push_subscriptions_pkey PRIMARY KEY (id),
    CONSTRAINT uk_push_subscriptions_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Secrets générés par l'application (clés VAPID du push) quand ils ne sont pas fournis en configuration
CREATE TABLE app_secrets (
    name  varchar(100) NOT NULL,
    value varchar(2000) NOT NULL,
    CONSTRAINT app_secrets_pkey PRIMARY KEY (name)
);

-- Préférences de notification : push, heures calmes (pas de push, la notification reste dans l'app)
CREATE TABLE notification_preferences (
    user_id     uuid    NOT NULL,
    push_enabled boolean NOT NULL DEFAULT true,
    quiet_start integer,
    quiet_end   integer,
    CONSTRAINT notification_preferences_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT notification_preferences_quiet_check CHECK (
        (quiet_start IS NULL AND quiet_end IS NULL)
        OR (quiet_start BETWEEN 0 AND 23 AND quiet_end BETWEEN 0 AND 23))
);

-- Alertes : nom libre, canal push, mise en sourdine, nouvelles conditions et période d'un an
ALTER TABLE alert_rules ADD COLUMN label varchar(100);
ALTER TABLE alert_rules ADD COLUMN notify_push boolean NOT NULL DEFAULT true;
ALTER TABLE alert_rules ADD COLUMN muted_until timestamp(6);

ALTER TABLE alert_rules DROP CONSTRAINT alert_rules_condition_check;
ALTER TABLE alert_rules ADD CONSTRAINT alert_rules_condition_check CHECK (condition_type IN (
    'RISES', 'FALLS', 'MOVES', 'ABOVE', 'BELOW',
    'PROFIT_ABOVE', 'LOSS_BELOW', 'NEW_HIGH', 'NEW_LOW', 'WEIGHT_ABOVE'));

ALTER TABLE alert_rules DROP CONSTRAINT alert_rules_period_check;
ALTER TABLE alert_rules ADD CONSTRAINT alert_rules_period_check
    CHECK (period IS NULL OR period IN ('DAY', 'WEEK', 'MONTH', 'YEAR'));
