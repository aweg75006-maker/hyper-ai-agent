package com.yzz.hyperaiagent.gateway.infrastructure.persistence;

import com.yzz.hyperaiagent.gateway.domain.quota.QuotaPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 调用方认证与配额的数据库访问集中在本仓库。 */
@Repository
public class GatewayConsumerRepository {

    private final JdbcTemplate jdbcTemplate;

    public GatewayConsumerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findEnabledConsumerIdByHash(String apiKeyHash) {
        List<String> ids = jdbcTemplate.query("""
                SELECT id FROM ai_api_consumer
                WHERE api_key_hash = ? AND enabled = TRUE
                """, (rs, rowNum) -> rs.getString("id"), apiKeyHash);
        return ids.stream().findFirst();
    }

    public boolean isEnabled(String consumerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_api_consumer WHERE id = ? AND enabled = TRUE
                """, Integer.class, consumerId);
        return count != null && count > 0;
    }

    /**
     * 按“Consumer + Route + Model > Consumer + Route > Consumer 默认”匹配最具体策略。
     */
    public Optional<QuotaPolicy> findQuota(String consumerId, String routeKey, String modelKey) {
        List<QuotaPolicy> policies = jdbcTemplate.query("""
                SELECT id, requests_per_minute, tokens_per_minute,
                       max_concurrent_requests, max_concurrent_streams, daily_token_quota
                FROM ai_quota_policy
                WHERE consumer_id = ? AND enabled = TRUE
                  AND (route_key IS NULL OR route_key = ?)
                  AND (model_key IS NULL OR model_key = ?)
                ORDER BY
                  CASE
                    WHEN route_key = ? AND model_key = ? THEN 3
                    WHEN route_key = ? AND model_key IS NULL THEN 2
                    WHEN route_key IS NULL AND model_key IS NULL THEN 1
                    ELSE 0
                  END DESC,
                  config_version DESC
                LIMIT 1
                """, this::mapQuota,
                consumerId, routeKey, modelKey, routeKey, modelKey, routeKey);
        return policies.stream().findFirst();
    }

    public void createConsumer(String id, String name, String apiKeyHash, String apiKeyPrefix) {
        jdbcTemplate.update("""
                INSERT INTO ai_api_consumer (id, name, api_key_hash, api_key_prefix, enabled)
                VALUES (?, ?, ?, ?, TRUE)
                """, id, name, apiKeyHash, apiKeyPrefix);
    }

    public void rotateKey(String consumerId, String apiKeyHash, String apiKeyPrefix) {
        int updated = jdbcTemplate.update("""
                UPDATE ai_api_consumer
                SET api_key_hash = ?, api_key_prefix = ?, config_version = config_version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, apiKeyHash, apiKeyPrefix, consumerId);
        if (updated != 1) {
            throw new IllegalArgumentException("调用方不存在: " + consumerId);
        }
    }

    public void touchLastUsed(String consumerId) {
        jdbcTemplate.update("UPDATE ai_api_consumer SET last_used_at = ? WHERE id = ?",
                OffsetDateTime.now(), consumerId);
    }

    private QuotaPolicy mapQuota(ResultSet rs, int rowNum) throws SQLException {
        return new QuotaPolicy(
                rs.getString("id"),
                rs.getInt("requests_per_minute"),
                (Integer) rs.getObject("tokens_per_minute"),
                rs.getInt("max_concurrent_requests"),
                rs.getInt("max_concurrent_streams"),
                (Long) rs.getObject("daily_token_quota")
        );
    }
}
