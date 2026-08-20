-- AI Gateway 的配置、治理和计量表。
-- 真实模型密钥禁止进入数据库，ai_provider_account 只保存环境变量引用。

CREATE TABLE IF NOT EXISTS ai_provider_account (
    id                  VARCHAR(64) PRIMARY KEY,
    provider_type       VARCHAR(32) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    base_url            VARCHAR(512),
    credential_ref      VARCHAR(128) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    status              VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    config_version      BIGINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_model_registration (
    id                      VARCHAR(64) PRIMARY KEY,
    model_key               VARCHAR(128) NOT NULL UNIQUE,
    provider_account_id     VARCHAR(64) NOT NULL REFERENCES ai_provider_account(id),
    provider_model_name     VARCHAR(128) NOT NULL,
    display_name            VARCHAR(128) NOT NULL,
    capabilities            VARCHAR(32)[] NOT NULL DEFAULT ARRAY[]::VARCHAR(32)[],
    context_window          INTEGER,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    priority                INTEGER NOT NULL DEFAULT 100,
    cost_level              NUMERIC(12, 6) NOT NULL DEFAULT 1.0,
    metadata                JSONB NOT NULL DEFAULT '{}'::JSONB,
    config_version          BIGINT NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_model_provider
    ON ai_model_registration(provider_account_id, enabled);

CREATE TABLE IF NOT EXISTS ai_route_policy (
    route_key                   VARCHAR(128) PRIMARY KEY,
    required_capabilities       VARCHAR(32)[] NOT NULL DEFAULT ARRAY[]::VARCHAR(32)[],
    timeout_ms                  BIGINT NOT NULL DEFAULT 60000,
    first_token_timeout_ms      BIGINT NOT NULL DEFAULT 15000,
    max_attempts                INTEGER NOT NULL DEFAULT 2,
    fallback_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    enabled                     BOOLEAN NOT NULL DEFAULT TRUE,
    config_version              BIGINT NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_route_target (
    id                      BIGSERIAL PRIMARY KEY,
    route_key               VARCHAR(128) NOT NULL REFERENCES ai_route_policy(route_key),
    model_registration_id   VARCHAR(64) NOT NULL REFERENCES ai_model_registration(id),
    target_order            INTEGER NOT NULL,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(route_key, model_registration_id),
    UNIQUE(route_key, target_order)
);

CREATE TABLE IF NOT EXISTS ai_api_consumer (
    id                  VARCHAR(64) PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    api_key_hash        VARCHAR(128),
    api_key_prefix      VARCHAR(24),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at        TIMESTAMPTZ,
    config_version      BIGINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_quota_policy (
    id                          VARCHAR(64) PRIMARY KEY,
    consumer_id                 VARCHAR(64) NOT NULL REFERENCES ai_api_consumer(id),
    route_key                   VARCHAR(128),
    model_key                   VARCHAR(128),
    requests_per_minute         INTEGER NOT NULL,
    tokens_per_minute           INTEGER,
    max_concurrent_requests     INTEGER NOT NULL,
    max_concurrent_streams      INTEGER NOT NULL,
    daily_token_quota           BIGINT,
    enabled                     BOOLEAN NOT NULL DEFAULT TRUE,
    config_version              BIGINT NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_quota_match
    ON ai_quota_policy(consumer_id, route_key, model_key, enabled);

CREATE TABLE IF NOT EXISTS ai_model_price (
    id                      VARCHAR(64) PRIMARY KEY,
    model_key               VARCHAR(128) NOT NULL,
    currency                VARCHAR(8) NOT NULL,
    unit_tokens             INTEGER NOT NULL,
    input_price             NUMERIC(24, 12) NOT NULL,
    output_price            NUMERIC(24, 12) NOT NULL,
    effective_from          TIMESTAMPTZ NOT NULL,
    effective_to            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX IF NOT EXISTS idx_ai_price_effective
    ON ai_model_price(model_key, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS ai_usage_record (
    id                      VARCHAR(64) PRIMARY KEY,
    request_id              VARCHAR(64) NOT NULL,
    consumer_id             VARCHAR(64),
    route_key               VARCHAR(128),
    provider_type           VARCHAR(32),
    model_key               VARCHAR(128),
    prompt_tokens           INTEGER,
    completion_tokens       INTEGER,
    total_tokens            INTEGER,
    usage_source            VARCHAR(32) NOT NULL,
    price_version_id        VARCHAR(64),
    currency                VARCHAR(8),
    input_cost              NUMERIC(24, 12),
    output_cost             NUMERIC(24, 12),
    total_cost              NUMERIC(24, 12),
    result                  VARCHAR(32) NOT NULL,
    fallback_count          INTEGER NOT NULL DEFAULT 0,
    duration_ms             BIGINT,
    error_code              VARCHAR(64),
    completed_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_request ON ai_usage_record(request_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_summary
    ON ai_usage_record(completed_at, consumer_id, route_key, model_key);

CREATE TABLE IF NOT EXISTS ai_gateway_audit_event (
    id                  VARCHAR(64) PRIMARY KEY,
    request_id          VARCHAR(64),
    event_type          VARCHAR(64) NOT NULL,
    route_key           VARCHAR(128),
    model_key           VARCHAR(128),
    attempt             INTEGER,
    error_code          VARCHAR(64),
    metadata            JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_audit_request ON ai_gateway_audit_event(request_id, created_at);
