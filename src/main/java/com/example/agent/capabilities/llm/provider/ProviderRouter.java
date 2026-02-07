package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.ModelDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 提供商路由器。
 *
 * <p>用途：根据模型定义在可用适配器中选择执行者。</p>
 */
@Component
public class ProviderRouter {

    private final List<ModelProviderAdapter> adapters;

    public ProviderRouter(List<ModelProviderAdapter> adapters) {
        this.adapters = adapters;
    }

    /**
     * 选择可用适配器。
     *
     * @param definition 模型定义
     * @return 适配器，未命中时返回 null
     */
    public ModelProviderAdapter route(ModelDefinition definition) {
        if (adapters == null || adapters.isEmpty()) {
            return null;
        }
        for (ModelProviderAdapter adapter : adapters) {
            if (adapter != null && adapter.supports(definition)) {
                return adapter;
            }
        }
        return null;
    }
}

