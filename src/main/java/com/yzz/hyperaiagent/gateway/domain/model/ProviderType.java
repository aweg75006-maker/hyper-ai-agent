package com.yzz.hyperaiagent.gateway.domain.model;

/** 模型服务提供方。新增 Provider 时需要同时提供对应的 Adapter。 */
public enum ProviderType {
    DASHSCOPE,
    OPENAI,
    OLLAMA
}
