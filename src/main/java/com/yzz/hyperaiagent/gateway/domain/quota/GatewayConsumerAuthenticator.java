package com.yzz.hyperaiagent.gateway.domain.quota;

import com.yzz.hyperaiagent.gateway.config.AiGatewayProperties;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayConsumerRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/** 将 Bearer Gateway Key 解析为调用方；Provider API Key 绝不能用于这里。 */
@Component
public class GatewayConsumerAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayConsumerRepository repository;
    private final GatewayApiKeyService apiKeyService;
    private final AiGatewayProperties properties;
    private final Environment environment;

    public GatewayConsumerAuthenticator(
            GatewayConsumerRepository repository,
            GatewayApiKeyService apiKeyService,
            AiGatewayProperties properties,
            Environment environment
    ) {
        this.repository = repository;
        this.apiKeyService = apiKeyService;
        this.properties = properties;
        this.environment = environment;
    }

    public String authenticate(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            if (anonymousLocalAllowed() && repository.isEnabled(properties.defaultConsumerId())) {
                return properties.defaultConsumerId();
            }
            throw new GatewayException(GatewayErrorCode.GATEWAY_UNAUTHORIZED, "缺少 Gateway Authorization");
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new GatewayException(GatewayErrorCode.GATEWAY_UNAUTHORIZED, "Authorization 必须使用 Bearer 格式");
        }
        String apiKey = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(apiKey)) {
            throw new GatewayException(GatewayErrorCode.GATEWAY_UNAUTHORIZED, "Gateway API Key 不能为空");
        }
        String consumerId = repository.findEnabledConsumerIdByHash(apiKeyService.hash(apiKey))
                .orElseThrow(() -> new GatewayException(
                        GatewayErrorCode.GATEWAY_UNAUTHORIZED,
                        "Gateway API Key 无效或已停用"
                ));
        repository.touchLastUsed(consumerId);
        return consumerId;
    }

    private boolean anonymousLocalAllowed() {
        // 双重条件：配置允许且确实激活 local Profile。生产环境误抄配置也不会放行匿名请求。
        return properties.allowAnonymousLocal()
                && Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}
