package com.yzz.hyperaiagent.gateway.api;

import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/** 首期管理 API 只允许 local Profile 下的环回地址访问。 */
@Component
public class AdminAccessGuard {

    private final Environment environment;

    public AdminAccessGuard(Environment environment) {
        this.environment = environment;
    }

    public void check(HttpServletRequest request) {
        boolean localProfile = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (!localProfile || !isLoopback(request.getRemoteAddr())) {
            throw new GatewayException(GatewayErrorCode.GATEWAY_UNAUTHORIZED,
                    "Gateway 管理 API 当前只允许本机 local 环境访问");
        }
    }

    private boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException ignored) {
            return false;
        }
    }
}
