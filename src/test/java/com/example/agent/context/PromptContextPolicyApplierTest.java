package com.example.agent.context;

import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.assembly.PromptAssemblyInput;
import com.example.agent.capabilities.context.model.RoleBoundary;
import com.example.agent.capabilities.context.model.TaskIntent;
import com.example.agent.capabilities.context.assembly.PromptContextPolicyApplier;
import com.example.agent.capabilities.llm.prompt.DefaultPromptTemplate;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PromptContextPolicyApplierTest {

    @Test
    void applySystemDeveloperAndUserShouldUseTemplateAndPreferredText() {
        PromptContextPolicyApplier applier = new PromptContextPolicyApplier(
                new MetricsPublisher(new SimpleMeterRegistry()));
        PromptAssemblyInput input = new PromptAssemblyInput();

        ContextSnapshot snapshot = new ContextSnapshot();
        RoleBoundary roleBoundary = new RoleBoundary();
        roleBoundary.setRiskLevel("high");
        snapshot.setRoleBoundary(roleBoundary);

        TaskIntent taskIntent = new TaskIntent();
        taskIntent.setInputText("快照用户输入");
        snapshot.setTaskIntent(taskIntent);

        applier.applySystemDeveloper(input, snapshot, new DefaultPromptTemplate(), false);
        applier.applyUserText(input, snapshot, "显式用户输入", false);

        assertNotNull(input.getSystemText());
        assertNotNull(input.getDeveloperText());
        assertEquals("显式用户输入", input.getUserText());
    }

    @Test
    void applyWhenBlankOnlyShouldPreserveExistingValues() {
        PromptContextPolicyApplier applier = new PromptContextPolicyApplier(null);
        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setSystemText("已有系统文本");
        input.setDeveloperText("已有开发者文本");
        input.setUserText("已有用户文本");

        ContextSnapshot snapshot = new ContextSnapshot();
        TaskIntent taskIntent = new TaskIntent();
        taskIntent.setInputText("快照用户输入");
        snapshot.setTaskIntent(taskIntent);

        applier.applySystemDeveloper(input, snapshot, new DefaultPromptTemplate(), true);
        applier.applyUserText(input, snapshot, "显式用户输入", true);

        assertEquals("已有系统文本", input.getSystemText());
        assertEquals("已有开发者文本", input.getDeveloperText());
        assertEquals("已有用户文本", input.getUserText());
    }

    @Test
    void applyUserTextShouldFallbackToSnapshot() {
        PromptContextPolicyApplier applier = new PromptContextPolicyApplier(null);
        PromptAssemblyInput input = new PromptAssemblyInput();

        ContextSnapshot snapshot = new ContextSnapshot();
        TaskIntent taskIntent = new TaskIntent();
        taskIntent.setInputText("快照兜底输入");
        snapshot.setTaskIntent(taskIntent);

        applier.applyUserText(input, snapshot, null, true);

        assertEquals("快照兜底输入", input.getUserText());
    }
}

