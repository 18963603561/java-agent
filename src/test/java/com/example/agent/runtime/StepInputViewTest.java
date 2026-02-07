package com.example.agent.runtime;

import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.step.RuntimeContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepInputViewTest {

    @Test
    void fromBuildsExecutionMapWithTypedApproval() {
        StepSpec step = new StepSpec();
        step.setStepType("TOOL");
        step.setArguments(Map.of("tool", "search", "query", "hello"));
        step.setContext(Map.of("requiresApproval", true, "approvalSource", "evaluation"));

        RuntimeContext runtimeContext = new RuntimeContext();
        StepInputView view = StepInputView.from(step, runtimeContext);
        Map<String, Object> executionMap = view.toExecutionMap();

        assertEquals("search", executionMap.get("tool"));
        assertEquals("hello", executionMap.get("query"));
        assertEquals(Boolean.TRUE, executionMap.get("requiresApproval"));
        assertEquals("evaluation", executionMap.get("approvalSource"));
        assertTrue(view.requiresApproval());
    }

    @Test
    void fromFallsBackToRuntimeApprovalWhenStepNotExplicit() {
        StepSpec step = new StepSpec();
        step.setStepType("TOOL");
        step.setArguments(Map.of("tool", "search"));

        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.asMap().put("requiresApproval", true);
        runtimeContext.asMap().put("approvalSource", "evaluation");

        StepInputView view = StepInputView.from(step, runtimeContext);
        Map<String, Object> executionMap = view.toExecutionMap();

        assertEquals(Boolean.TRUE, executionMap.get("requiresApproval"));
        assertEquals("evaluation", executionMap.get("approvalSource"));
        assertTrue(view.requiresApproval());
    }
}

