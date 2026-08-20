package com.yzz.hyperaiagent.gateway.infrastructure.secret;

/** 密钥解析扩展点；Provider 配置只能持有引用名。 */
public interface CredentialResolver {

    boolean exists(String credentialRef);
}
