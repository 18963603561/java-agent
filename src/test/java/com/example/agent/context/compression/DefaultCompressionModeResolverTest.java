package com.example.agent.context.compression;

import com.example.agent.budget.trim.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.application.DefaultCompressionModeResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 默认压缩模式解析器测试。
 */
class DefaultCompressionModeResolverTest {

    @Test
    void shouldUseConfiguredModeWhenEmergencySwitchOff() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getMode().setType("llm");

        DefaultCompressionModeResolver resolver = new DefaultCompressionModeResolver(properties);

        assertEquals("llm", resolver.resolveMode());
    }

    @Test
    void shouldForceRuleWhenEmergencySwitchOn() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getMode().setType("llm");
        properties.getEmergency().setForceRuleMode(true);

        DefaultCompressionModeResolver resolver = new DefaultCompressionModeResolver(properties);

        assertEquals("rule", resolver.resolveMode());
    }
}

