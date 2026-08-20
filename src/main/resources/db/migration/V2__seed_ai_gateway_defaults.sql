-- 本地首期默认配置。INSERT 均为幂等操作，管理 API 后续修改的数据不会被启动过程覆盖。
INSERT INTO ai_provider_account (
    id, provider_type, name, base_url, credential_ref, enabled, status
) VALUES (
    'provider-dashscope-default', 'DASHSCOPE', '阿里云百炼（默认账号）',
    'https://dashscope.aliyuncs.com', 'AI_DASHSCOPE_API_KEY', TRUE, 'UNKNOWN'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_model_registration (
    id, model_key, provider_account_id, provider_model_name, display_name,
    capabilities, context_window, enabled, priority, cost_level
) VALUES
    ('model-dashscope-qwen-flash', 'dashscope-qwen-flash', 'provider-dashscope-default',
     'qwen-flash', 'Qwen Flash', ARRAY['CHAT', 'STREAM', 'TOOLS'], 1000000, TRUE, 10, 1.0),
    ('model-dashscope-qwen-plus', 'dashscope-qwen-plus', 'provider-dashscope-default',
     'qwen-plus', 'Qwen Plus', ARRAY['CHAT', 'STREAM', 'TOOLS', 'JSON_MODE'], 1000000, TRUE, 20, 2.0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_route_policy (
    route_key, required_capabilities, timeout_ms, first_token_timeout_ms,
    max_attempts, fallback_enabled, enabled
) VALUES
    ('general-chat', ARRAY['CHAT'], 60000, 15000, 2, TRUE, TRUE),
    ('psychology-chat', ARRAY['CHAT'], 60000, 15000, 2, TRUE, TRUE),
    ('pdf-rag', ARRAY['CHAT'], 90000, 20000, 2, TRUE, TRUE),
    ('agent-tool-calling', ARRAY['CHAT', 'TOOLS'], 120000, 30000, 2, TRUE, TRUE)
ON CONFLICT (route_key) DO NOTHING;

INSERT INTO ai_route_target (route_key, model_registration_id, target_order, enabled) VALUES
    ('general-chat', 'model-dashscope-qwen-flash', 1, TRUE),
    ('general-chat', 'model-dashscope-qwen-plus', 2, TRUE),
    ('psychology-chat', 'model-dashscope-qwen-plus', 1, TRUE),
    ('psychology-chat', 'model-dashscope-qwen-flash', 2, TRUE),
    ('pdf-rag', 'model-dashscope-qwen-plus', 1, TRUE),
    ('pdf-rag', 'model-dashscope-qwen-flash', 2, TRUE),
    ('agent-tool-calling', 'model-dashscope-qwen-plus', 1, TRUE),
    ('agent-tool-calling', 'model-dashscope-qwen-flash', 2, TRUE)
ON CONFLICT (route_key, model_registration_id) DO NOTHING;

INSERT INTO ai_api_consumer (id, name, enabled)
VALUES ('local-system', '本地可信调用方', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_quota_policy (
    id, consumer_id, requests_per_minute, max_concurrent_requests,
    max_concurrent_streams, enabled
) VALUES (
    'quota-local-system-default', 'local-system', 60, 8, 4, TRUE
) ON CONFLICT (id) DO NOTHING;
