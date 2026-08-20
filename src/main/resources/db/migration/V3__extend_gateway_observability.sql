-- 扩展 Gateway 审计表，使一次请求能够从统计数据下钻到 Trace 和具体治理事件。
-- metadata 只保存白名单治理字段，禁止写入 Prompt、模型回复和 API Key。

ALTER TABLE ai_gateway_audit_event
    ADD COLUMN IF NOT EXISTS consumer_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS span_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT;

CREATE INDEX IF NOT EXISTS idx_ai_audit_trace
    ON ai_gateway_audit_event(trace_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_audit_time_type
    ON ai_gateway_audit_event(created_at, event_type);

CREATE INDEX IF NOT EXISTS idx_ai_usage_time_result
    ON ai_usage_record(completed_at, result);
