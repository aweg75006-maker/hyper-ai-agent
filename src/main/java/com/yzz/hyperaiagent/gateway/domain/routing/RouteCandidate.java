package com.yzz.hyperaiagent.gateway.domain.routing;

import com.yzz.hyperaiagent.gateway.domain.model.ModelRegistration;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderAccount;

/** 路由计划中的单个候选，包含完整调用信息和可审计的选择原因。 */
public record RouteCandidate(
        int order,
        ModelRegistration model,
        ProviderAccount provider,
        String reason
) {
}
