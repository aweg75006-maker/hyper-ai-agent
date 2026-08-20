package com.yzz.hyperaiagent.gateway.domain.model;

/** Provider 的运行状态；UNKNOWN 表示尚未探测，不等同于不可用。 */
public enum ProviderStatus {
    UNKNOWN,
    UP,
    DEGRADED,
    DOWN
}
