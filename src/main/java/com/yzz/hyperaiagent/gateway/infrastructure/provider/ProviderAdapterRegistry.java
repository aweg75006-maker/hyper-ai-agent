package com.yzz.hyperaiagent.gateway.infrastructure.provider;

import com.yzz.hyperaiagent.gateway.domain.model.ProviderType;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 按 ProviderType 精确选择 Adapter，不通过类名或模型名前缀进行猜测。 */
@Component
public class ProviderAdapterRegistry {

    private final Map<ProviderType, ModelProviderAdapter> adapters;

    public ProviderAdapterRegistry(List<ModelProviderAdapter> adapters) {
        Map<ProviderType, ModelProviderAdapter> indexed = new EnumMap<>(ProviderType.class);
        for (ModelProviderAdapter adapter : adapters) {
            ModelProviderAdapter previous = indexed.put(adapter.providerType(), adapter);
            if (previous != null) {
                throw new IllegalStateException("Provider Adapter 重复注册: " + adapter.providerType());
            }
        }
        this.adapters = Map.copyOf(indexed);
    }

    public ModelProviderAdapter required(ProviderType providerType) {
        ModelProviderAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw new GatewayException(GatewayErrorCode.NO_AVAILABLE_MODEL,
                    "当前服务未安装 Provider Adapter: " + providerType);
        }
        return adapter;
    }
}
