package com.yzz.hyperaiagent.gateway.application;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatRequest;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatResponse;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayStreamEvent;
import com.yzz.hyperaiagent.gateway.domain.routing.RoutePlan;
import reactor.core.publisher.Flux;

/** 单体内业务应直接调用该服务，禁止通过 HTTP 回调本机 Controller。 */
public interface AiGatewayService {

    GatewayChatResponse complete(GatewayChatRequest request, String consumerId);

    Flux<GatewayStreamEvent> stream(GatewayChatRequest request, String consumerId);

    RoutePlan simulate(GatewayChatRequest request);
}
