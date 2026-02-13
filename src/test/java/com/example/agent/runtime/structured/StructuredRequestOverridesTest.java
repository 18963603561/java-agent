package com.example.agent.runtime.structured;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 结构化覆盖配置解析测试。
 */
class StructuredRequestOverridesTest {

    @Test
    void shouldResolveOverridesFromContext() {
        // 构建覆盖配置映射。
        Map<String, Object> overrides = Map.of("enabled", false);
        // 构建上下文映射。
        Map<String, Object> context = Map.of("structured", overrides);
        // 构建步骤输入映射。
        Map<String, Object> stepInput = Map.of("context", context);

        // 调用覆盖解析器解析结构化覆盖配置。
        StructuredRequestOverrides resolved = StructuredRequestOverrides.fromStepInput(stepInput);
        // 校验启用开关为 false。
        assertEquals(false, resolved.getEnabled());
    }

    @Test
    void shouldReturnNullWhenOverridesMissing() {
        // 构建空步骤输入映射。
        Map<String, Object> stepInput = Map.of();

        // 调用覆盖解析器解析结构化覆盖配置。
        StructuredRequestOverrides resolved = StructuredRequestOverrides.fromStepInput(stepInput);
        // 校验覆盖配置为空。
        assertNull(resolved);
    }
}
