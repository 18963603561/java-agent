package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.config.ModelProviderHttpProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型接口密钥解析器。
 *
 * <p>用途：统一处理模型级密钥与全局密钥优先级。</p>
 */
@Component
public class ModelApiKeyResolver {

    private final ModelProviderHttpProperties httpProperties;

    public ModelApiKeyResolver(ModelProviderHttpProperties httpProperties) {
        this.httpProperties = httpProperties;
    }

    /**
     * 解析最终接口密钥。
     *
     * @param definition 模型定义
     * @return 接口密钥，未配置时返回 null
     */
    public String resolve(ModelDefinition definition) {
        if (definition != null && StringUtils.hasText(definition.getApiKey())) {
            return definition.getApiKey();
        }
        if (StringUtils.hasText(httpProperties.getApiKey())) {
            return httpProperties.getApiKey();
        }
        return null;
    }
}
