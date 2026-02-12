package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.application.model.LlmCompressionCommand;
import com.example.agent.capabilities.context.compression.prompt.DefaultCompressionPromptBuilder;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.WorkingMemory;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认压缩提示词构建器测试。
 */
class DefaultCompressionPromptBuilderTest {

    @Test
    void shouldBuildPromptWithSnapshotDetails() {
        DefaultCompressionPromptBuilder builder = new DefaultCompressionPromptBuilder();

        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setSummary("工作记忆摘要");
        MemoryRef ref = new MemoryRef();
        ref.setMemoryId("m1");
        ref.setSnippet("记忆片段");
        LongTermMemory longTermMemory = new LongTermMemory();
        longTermMemory.setMemoryRefs(List.of(ref));
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(workingMemory);
        snapshot.setLongTermMemory(longTermMemory);

        LlmCompressionCommand command = new LlmCompressionCommand();
        command.setSnapshot(snapshot);
        command.setWorkflowId("wf-1");
        command.setSessionId("s-1");
        command.setTriggerReason("OVER_TOTAL");

        String prompt = builder.buildPrompt(command);

        assertTrue(prompt.contains("workflowId=wf-1"));
        assertTrue(prompt.contains("workingSummary=工作记忆摘要"));
        assertTrue(prompt.contains("m1:记忆片段"));
    }
}

