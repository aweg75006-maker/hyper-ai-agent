package com.yzz.hyperaiagent.gateway.infrastructure.persistence;

import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;
import com.yzz.hyperaiagent.gateway.domain.model.ModelRegistration;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderAccount;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderStatus;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderType;
import com.yzz.hyperaiagent.gateway.domain.model.RoutePolicy;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistrySnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gateway 配置仓库。
 *
 * <p>这里集中维护 SQL 映射，领域层和路由层均不依赖 JdbcTemplate。配置写入完成后由
 * {@code ModelRegistry} 一次性发布新快照。</p>
 */
@Repository
public class GatewayConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public GatewayConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ModelRegistrySnapshot loadSnapshot(long snapshotVersion) {
        Map<String, ProviderAccount> providers = loadProviders();
        Map<String, ModelRegistration> models = loadModels();
        Map<String, RoutePolicy> routes = loadRoutes();
        return new ModelRegistrySnapshot(snapshotVersion, Instant.now(), providers, models, routes);
    }

    private Map<String, ProviderAccount> loadProviders() {
        Map<String, ProviderAccount> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT id, provider_type, name, base_url, credential_ref,
                       enabled, status, config_version
                FROM ai_provider_account
                ORDER BY id
                """, rs -> {
            ProviderAccount provider = new ProviderAccount(
                    rs.getString("id"),
                    ProviderType.valueOf(rs.getString("provider_type")),
                    rs.getString("name"),
                    rs.getString("base_url"),
                    rs.getString("credential_ref"),
                    rs.getBoolean("enabled"),
                    ProviderStatus.valueOf(rs.getString("status")),
                    rs.getLong("config_version")
            );
            result.put(provider.id(), provider);
        });
        return result;
    }

    private Map<String, ModelRegistration> loadModels() {
        Map<String, ModelRegistration> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT id, model_key, provider_account_id, provider_model_name, display_name,
                       capabilities, context_window, enabled, priority, cost_level, config_version
                FROM ai_model_registration
                ORDER BY priority, model_key
                """, rs -> {
            Integer contextWindow = (Integer) rs.getObject("context_window");
            ModelRegistration model = new ModelRegistration(
                    rs.getString("id"),
                    rs.getString("model_key"),
                    rs.getString("provider_account_id"),
                    rs.getString("provider_model_name"),
                    rs.getString("display_name"),
                    readCapabilities(rs.getArray("capabilities")),
                    contextWindow,
                    rs.getBoolean("enabled"),
                    rs.getInt("priority"),
                    rs.getBigDecimal("cost_level"),
                    rs.getLong("config_version")
            );
            result.put(model.modelKey(), model);
        });
        return result;
    }

    private Map<String, RoutePolicy> loadRoutes() {
        Map<String, List<String>> targets = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT target.route_key, model.model_key
                FROM ai_route_target target
                JOIN ai_model_registration model ON model.id = target.model_registration_id
                WHERE target.enabled = TRUE
                ORDER BY target.route_key, target.target_order, model.priority
                """, (RowCallbackHandler) rs -> {
            targets.computeIfAbsent(rs.getString("route_key"), ignored -> new ArrayList<>())
                    .add(rs.getString("model_key"));
        });

        Map<String, RoutePolicy> routes = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT route_key, required_capabilities, timeout_ms, first_token_timeout_ms,
                       max_attempts, fallback_enabled, enabled, config_version
                FROM ai_route_policy
                ORDER BY route_key
                """, rs -> {
            String routeKey = rs.getString("route_key");
            RoutePolicy policy = new RoutePolicy(
                    routeKey,
                    readCapabilities(rs.getArray("required_capabilities")),
                    Duration.ofMillis(rs.getLong("timeout_ms")),
                    Duration.ofMillis(rs.getLong("first_token_timeout_ms")),
                    rs.getInt("max_attempts"),
                    rs.getBoolean("fallback_enabled"),
                    rs.getBoolean("enabled"),
                    rs.getLong("config_version"),
                    targets.getOrDefault(routeKey, List.of())
            );
            routes.put(routeKey, policy);
        });
        return routes;
    }

    /** 新增或更新 Provider；credentialRef 是引用名，不接受真实密钥字段。 */
    public void saveProvider(ProviderAccount provider) {
        jdbcTemplate.update("""
                INSERT INTO ai_provider_account (
                    id, provider_type, name, base_url, credential_ref, enabled, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    provider_type = EXCLUDED.provider_type,
                    name = EXCLUDED.name,
                    base_url = EXCLUDED.base_url,
                    credential_ref = EXCLUDED.credential_ref,
                    enabled = EXCLUDED.enabled,
                    status = EXCLUDED.status,
                    config_version = ai_provider_account.config_version + 1,
                    updated_at = CURRENT_TIMESTAMP
                """, provider.id(), provider.providerType().name(), provider.name(), provider.baseUrl(),
                provider.credentialRef(), provider.enabled(), provider.status().name());
    }

    /** 新增或更新模型能力，能力集合由枚举产生，避免任意字符串进入路由判断。 */
    public void saveModel(ModelRegistration model) {
        jdbcTemplate.update("""
                INSERT INTO ai_model_registration (
                    id, model_key, provider_account_id, provider_model_name, display_name,
                    capabilities, context_window, enabled, priority, cost_level
                ) VALUES (?, ?, ?, ?, ?, string_to_array(?, ',')::VARCHAR(32)[], ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    model_key = EXCLUDED.model_key,
                    provider_account_id = EXCLUDED.provider_account_id,
                    provider_model_name = EXCLUDED.provider_model_name,
                    display_name = EXCLUDED.display_name,
                    capabilities = EXCLUDED.capabilities,
                    context_window = EXCLUDED.context_window,
                    enabled = EXCLUDED.enabled,
                    priority = EXCLUDED.priority,
                    cost_level = EXCLUDED.cost_level,
                    config_version = ai_model_registration.config_version + 1,
                    updated_at = CURRENT_TIMESTAMP
                """, model.id(), model.modelKey(), model.providerAccountId(), model.providerModelName(),
                model.displayName(), joinCapabilities(model.capabilities()), model.contextWindow(), model.enabled(),
                model.priority(), model.costLevel());
    }

    /** 路由策略和候选目标必须在同一事务中替换，避免在线快照读到半套配置。 */
    @Transactional
    public void saveRoute(RoutePolicy policy) {
        jdbcTemplate.update("""
                INSERT INTO ai_route_policy (
                    route_key, required_capabilities, timeout_ms, first_token_timeout_ms,
                    max_attempts, fallback_enabled, enabled
                ) VALUES (?, string_to_array(?, ',')::VARCHAR(32)[], ?, ?, ?, ?, ?)
                ON CONFLICT (route_key) DO UPDATE SET
                    required_capabilities = EXCLUDED.required_capabilities,
                    timeout_ms = EXCLUDED.timeout_ms,
                    first_token_timeout_ms = EXCLUDED.first_token_timeout_ms,
                    max_attempts = EXCLUDED.max_attempts,
                    fallback_enabled = EXCLUDED.fallback_enabled,
                    enabled = EXCLUDED.enabled,
                    config_version = ai_route_policy.config_version + 1,
                    updated_at = CURRENT_TIMESTAMP
                """, policy.routeKey(), joinCapabilities(policy.requiredCapabilities()), policy.timeout().toMillis(),
                policy.firstTokenTimeout().toMillis(), policy.maxAttempts(), policy.fallbackEnabled(), policy.enabled());

        jdbcTemplate.update("DELETE FROM ai_route_target WHERE route_key = ?", policy.routeKey());
        for (int index = 0; index < policy.targetModelKeys().size(); index++) {
            jdbcTemplate.update("""
                    INSERT INTO ai_route_target (route_key, model_registration_id, target_order, enabled)
                    SELECT ?, id, ?, TRUE FROM ai_model_registration WHERE model_key = ?
                    """, policy.routeKey(), index + 1, policy.targetModelKeys().get(index));
        }
    }

    private Set<ModelCapability> readCapabilities(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return Set.of();
        }
        Object[] rawValues = (Object[]) sqlArray.getArray();
        return Arrays.stream(rawValues)
                .map(Object::toString)
                .map(ModelCapability::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String joinCapabilities(Set<ModelCapability> capabilities) {
        return capabilities.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
