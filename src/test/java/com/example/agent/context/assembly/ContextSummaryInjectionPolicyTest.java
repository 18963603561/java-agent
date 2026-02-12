package com.example.agent.context.assembly;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.capabilities.context.assembly.DefaultContextSummaryInjectionPolicy;
import com.example.agent.capabilities.context.assembly.PromptAssemblyInput;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.WorkingMemory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上下文摘要注入策略测试。
 */
class ContextSummaryInjectionPolicyTest {

    @Test
    void shouldInjectSummaryToDeveloperTextWhenEnabled() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getInjection().setEnabled(true);
        properties.getInjection().setMaxChars(100);
        DefaultContextSummaryInjectionPolicy policy = new DefaultContextSummaryInjectionPolicy(properties, null);

        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setDeveloperText("原始开发者提示");
        ContextSnapshot snapshot = new ContextSnapshot();
        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setSummary("这是压缩后的上下文摘要");
        snapshot.setWorkingMemory(workingMemory);
        ContextCompressionResult compressionResult = new ContextCompressionResult();

        policy.inject(input, snapshot, compressionResult);

        assertNotNull(input.getDeveloperText());
        assertTrue(input.getDeveloperText().contains("[context_summary]"));
        assertTrue(input.getDeveloperText().contains("这是压缩后的上下文摘要"));
        assertEquals("这是压缩后的上下文摘要", input.getContextSummaryText());
        assertTrue(compressionResult.isSummaryInjected());
        assertEquals("SUMMARY_INJECTED", compressionResult.getSummaryInjectReason());
    }

    @Test
    void shouldSkipInjectionWhenDisabled() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getInjection().setEnabled(false);
        DefaultContextSummaryInjectionPolicy policy = new DefaultContextSummaryInjectionPolicy(properties, null);

        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setDeveloperText("原始开发者提示");
        ContextSnapshot snapshot = new ContextSnapshot();
        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setSummary("摘要不会注入");
        snapshot.setWorkingMemory(workingMemory);
        ContextCompressionResult compressionResult = new ContextCompressionResult();

        policy.inject(input, snapshot, compressionResult);

        assertEquals("原始开发者提示", input.getDeveloperText());
        assertFalse(compressionResult.isSummaryInjected());
        assertEquals("INJECTION_DISABLED", compressionResult.getSummaryInjectReason());
    }

    @Test
    void shouldSkipInjectionWhenSummaryEmpty() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getInjection().setEnabled(true);
        DefaultContextSummaryInjectionPolicy policy = new DefaultContextSummaryInjectionPolicy(properties, null);

        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setDeveloperText("原始开发者提示");
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(new WorkingMemory());
        ContextCompressionResult compressionResult = new ContextCompressionResult();

        policy.inject(input, snapshot, compressionResult);

        assertEquals("原始开发者提示", input.getDeveloperText());
        assertFalse(compressionResult.isSummaryInjected());
        assertEquals("SUMMARY_EMPTY", compressionResult.getSummaryInjectReason());
    }
}



