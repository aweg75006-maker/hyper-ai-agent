package com.yzz.hyperaiagent.gateway.api;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatRequest;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatResponse;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayStreamEvent;
import com.yzz.hyperaiagent.gateway.application.AiGatewayService;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayConsumerAuthenticator;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** AI Gateway 的稳定运行时入口。 */
@RestController
@RequestMapping("/gateway/v1")
public class AiGatewayController {

    private final AiGatewayService gatewayService;
    private final GatewayConsumerAuthenticator authenticator;

    public AiGatewayController(AiGatewayService gatewayService, GatewayConsumerAuthenticator authenticator) {
        this.gatewayService = gatewayService;
        this.authenticator = authenticator;
    }

    @PostMapping(value = "/chat/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public GatewayChatResponse complete(
            @Valid @RequestBody GatewayChatRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String consumerId = authenticator.authenticate(authorization);
        return gatewayService.complete(request, consumerId);
    }

    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<GatewayStreamEvent>> stream(
            @Valid @RequestBody GatewayChatRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String consumerId = authenticator.authenticate(authorization);
        return gatewayService.stream(request, consumerId)
                .map(event -> ServerSentEvent.<GatewayStreamEvent>builder()
                        .event(event.event())
                        .data(event)
                        .build());
    }
}
