package com.yzz.hyperaiagent.gateway.domain.quota;

/** 一条已按 Consumer、Route、Model 匹配完成的配额策略。 */
public record QuotaPolicy(
        String id,
        int requestsPerMinute,
        Integer tokensPerMinute,
        int maxConcurrentRequests,
        int maxConcurrentStreams,
        Long dailyTokenQuota
) {
}
