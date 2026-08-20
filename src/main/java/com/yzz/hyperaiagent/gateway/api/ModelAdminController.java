package com.yzz.hyperaiagent.gateway.api;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayAdminRequests.SaveModel;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayAdminRequests.SaveProvider;
import com.yzz.hyperaiagent.gateway.application.GatewayAdminService;
import com.yzz.hyperaiagent.gateway.domain.model.ModelRegistration;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/** Provider 与模型注册管理；返回信息只包含 credentialRef，不包含真实凭据。 */
@RestController
@RequestMapping("/gateway/admin")
public class ModelAdminController {

    private final AdminAccessGuard accessGuard;
    private final GatewayAdminService adminService;

    public ModelAdminController(AdminAccessGuard accessGuard, GatewayAdminService adminService) {
        this.accessGuard = accessGuard;
        this.adminService = adminService;
    }

    @GetMapping("/providers")
    public Collection<ProviderAccount> providers(HttpServletRequest servletRequest) {
        accessGuard.check(servletRequest);
        return adminService.snapshot().providersById().values();
    }

    @PostMapping("/providers")
    public ProviderAccount saveProvider(
            @Valid @RequestBody SaveProvider request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.check(servletRequest);
        return adminService.saveProvider(request).providersById().get(request.id());
    }

    @GetMapping("/models")
    public Collection<ModelRegistration> models(HttpServletRequest servletRequest) {
        accessGuard.check(servletRequest);
        return adminService.snapshot().modelsByKey().values();
    }

    @PostMapping("/models")
    public ModelRegistration saveModel(
            @Valid @RequestBody SaveModel request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.check(servletRequest);
        return adminService.saveModel(request).modelsByKey().get(request.modelKey());
    }

    @PostMapping("/models/{modelKey}/enable")
    public ModelRegistration enable(@PathVariable String modelKey, HttpServletRequest servletRequest) {
        return setEnabled(modelKey, true, servletRequest);
    }

    @PostMapping("/models/{modelKey}/disable")
    public ModelRegistration disable(@PathVariable String modelKey, HttpServletRequest servletRequest) {
        return setEnabled(modelKey, false, servletRequest);
    }

    private ModelRegistration setEnabled(String modelKey, boolean enabled, HttpServletRequest servletRequest) {
        accessGuard.check(servletRequest);
        ModelRegistration current = adminService.snapshot().modelsByKey().get(modelKey);
        if (current == null) {
            throw new com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException(
                    com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode.MODEL_NOT_FOUND,
                    "模型不存在: " + modelKey
            );
        }
        SaveModel update = new SaveModel(
                current.id(), current.modelKey(), current.providerAccountId(), current.providerModelName(),
                current.displayName(), current.capabilities(), current.contextWindow(), enabled,
                current.priority(), current.costLevel()
        );
        return adminService.saveModel(update).modelsByKey().get(modelKey);
    }
}
