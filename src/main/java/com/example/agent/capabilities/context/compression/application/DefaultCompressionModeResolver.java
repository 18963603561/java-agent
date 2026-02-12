package com.example.agent.capabilities.context.compression.application;

import com.example.agent.budget.trim.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.application.port.CompressionModeResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认压缩模式解析器。
 */
@Component
public class DefaultCompressionModeResolver implements CompressionModeResolver {

    private static final String MODE_RULE = "rule";

    /**
     * 压缩配置属性。
     */
    private final ContextCompressionProperties properties;

    public DefaultCompressionModeResolver(ContextCompressionProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolveMode() {
        // 紧急策略判定：当启用强制规则模式时直接回退到 rule。
        if (properties != null
                && properties.getEmergency() != null
                && properties.getEmergency().isForceRuleMode()) {
            return MODE_RULE;
        }
        // 空配置兜底：缺失模式配置时直接回退 rule。
        if (properties == null || properties.getMode() == null) {
            return MODE_RULE;
        }
        String configuredMode = properties.getMode().getType();
        // 兜底策略：配置为空时回退到 rule，保证老链路稳定。
        if (!StringUtils.hasText(configuredMode)) {
            return MODE_RULE;
        }
        return configuredMode.trim().toLowerCase();
    }
}
