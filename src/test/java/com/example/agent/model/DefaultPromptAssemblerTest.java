package com.example.agent.model;

import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextSection;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.PromptAssemblyInput;
import com.example.agent.capabilities.context.RoleBoundary;
import com.example.agent.capabilities.memory.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.agent.capabilities.llm.DefaultPromptAssembler;
import com.example.agent.capabilities.llm.DefaultPromptTemplate;
import com.example.agent.capabilities.llm.PromptBundle;
import com.example.agent.capabilities.llm.PromptRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPromptAssemblerTest {

    @Test
    void buildAddsSystemDeveloperAndUserMessages() {
        DefaultPromptTemplate template = new DefaultPromptTemplate();
        ReflectionTestUtils.setField(template, "systemMessage", "系统策略");
        ReflectionTestUtils.setField(template, "developerMessage", "开发者策略");

        DefaultPromptAssembler assembler = new DefaultPromptAssembler(template,
                new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()));

        ContextSnapshot snapshot = new ContextSnapshot();
        RoleBoundary boundary = new RoleBoundary();
        boundary.setRiskLevel("low");
        snapshot.setRoleBoundary(boundary);

        TaskRequest taskRequest = new TaskRequest();
        Map<String, Object> context = new HashMap<>();
        context.put("contextSnapshot", snapshot);
        taskRequest.setContext(context);

        PromptBundle bundle = assembler.build("用户问题", taskRequest, null);

        assertNotNull(bundle);
        assertEquals(3, bundle.getMessages().size());
        assertEquals(PromptRole.SYSTEM, bundle.getMessages().get(0).getRole());
        assertEquals(PromptRole.DEVELOPER, bundle.getMessages().get(1).getRole());
        assertEquals(PromptRole.USER, bundle.getMessages().get(2).getRole());
    }

    @Test
    void buildTrimsWhenOverBudget() {
        DefaultPromptAssembler assembler = new DefaultPromptAssembler(new DefaultPromptTemplate(),
                new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(assembler, "promptTrimEnabled", true);
        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setSystemText("系统策略:必须遵守安全边界");
        input.setDeveloperText("开发者策略\n" + "A".repeat(200));
        String longUser = "用户问题:" + "B".repeat(200);
        input.setUserText(longUser);
        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        EnumMap<ContextSection, Integer> sections = new EnumMap<>(ContextSection.class);
        sections.put(ContextSection.SYSTEM_POLICY, 4);
        sections.put(ContextSection.DEVELOPER_POLICY, 4);
        sections.put(ContextSection.USER_INPUT, 4);
        allocation.setSectionTokens(sections);
        input.setBudgetAllocation(allocation);
        Map<String, Object> stepInput = new HashMap<>();
        stepInput.put("promptAssemblyInput", input);

        PromptBundle bundle = assembler.build("ignored", null, stepInput);

        assertNotNull(bundle);
        assertNotNull(bundle.getTruncatedSections());
        assertFalse(bundle.getTruncatedSections().isEmpty());
        assertEquals(3, bundle.getMessages().size());
        assertEquals(PromptRole.SYSTEM, bundle.getMessages().get(0).getRole());
        assertEquals(PromptRole.DEVELOPER, bundle.getMessages().get(1).getRole());
        assertEquals(PromptRole.USER, bundle.getMessages().get(2).getRole());
    }

    @Test
    void buildDoesNotTrimWhenDisabled() {
        DefaultPromptAssembler assembler = new DefaultPromptAssembler(new DefaultPromptTemplate(),
                new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(assembler, "promptTrimEnabled", false);

        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setSystemText("系统策略");
        input.setDeveloperText("开发者策略");
        String longUser = "用户问题:" + "C".repeat(200);
        input.setUserText(longUser);
        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        EnumMap<ContextSection, Integer> sections = new EnumMap<>(ContextSection.class);
        sections.put(ContextSection.SYSTEM_POLICY, 1);
        sections.put(ContextSection.DEVELOPER_POLICY, 1);
        sections.put(ContextSection.USER_INPUT, 1);
        allocation.setSectionTokens(sections);
        input.setBudgetAllocation(allocation);
        input.setTruncatedSections(List.of());
        Map<String, Object> stepInput = new HashMap<>();
        stepInput.put("promptAssemblyInput", input);

        PromptBundle bundle = assembler.build("ignored", null, stepInput);

        assertNotNull(bundle);
        assertTrue(bundle.getTruncatedSections().isEmpty());
        assertEquals(longUser, bundle.getMessages().get(2).getContent());
    }
}
