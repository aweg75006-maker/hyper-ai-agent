package com.yzz.hyperaiagent.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 模块的统一配置入口。
 *
 * <p>当前模块仍运行在项目原有的 Spring MVC 中，不额外引入一套 WebFlux 服务容器。</p>
 */
@Configuration
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiGatewayConfiguration {
}
