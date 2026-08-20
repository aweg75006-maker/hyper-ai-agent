package com.yzz.hyperaiagent.gateway.infrastructure.secret;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 从 Spring Environment（包括项目根目录下被忽略的 .env）解析密钥引用。 */
@Component
public class EnvironmentCredentialResolver implements CredentialResolver {

    private final Environment environment;

    public EnvironmentCredentialResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean exists(String credentialRef) {
        if (!StringUtils.hasText(credentialRef)) {
            return false;
        }
        // 这里只返回是否存在，调用链、日志和管理 API 均不会拿到或输出真实密钥值。
        return StringUtils.hasText(environment.getProperty(credentialRef));
    }
}
