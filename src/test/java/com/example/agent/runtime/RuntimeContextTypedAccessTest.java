package com.example.agent.runtime;

import com.example.agent.runtime.model.input.ApprovalInput;
import com.example.agent.runtime.model.input.LastStepInput;
import com.example.agent.runtime.step.RuntimeContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextTypedAccessTest {

    @Test
    void approvalInputRoundTripWorks() {
        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.setApprovalInput(ApprovalInput.of(true, "evaluation"));

        ApprovalInput approvalInput = runtimeContext.getApprovalInput();
        assertTrue(approvalInput.isRequired());
        assertEquals("evaluation", approvalInput.getApprovalSource());
    }

    @Test
    void lastStepSnapshotRoundTripWorks() {
        RuntimeContext runtimeContext = new RuntimeContext();
        LastStepInput snapshot = new LastStepInput();
        snapshot.setStepId("s-1");
        snapshot.setStepType("TOOL");
        snapshot.setSummary(Map.of("summary", "ok"));
        snapshot.setRawRef("rawref:v1:mem:raw:1");
        snapshot.setRawRefs(Map.of("decisionRawRef", "rawref:v1:mem:raw:decision"));
        snapshot.setRawTruncated(false);
        snapshot.setOutputSize(3);

        runtimeContext.setLastStepInput(snapshot);
        LastStepInput read = runtimeContext.getLastStepInput();

        assertEquals("s-1", read.getStepId());
        assertEquals("TOOL", read.getStepType());
        assertEquals("rawref:v1:mem:raw:1", read.getRawRef());
        assertFalse(read.isRawTruncated());
        assertEquals(3, read.getOutputSize());
    }

    @Test
    void stepsRoundTripWorks() {
        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.setSteps(List.of(
                Map.of("stepId", "s-1", "type", "TOOL"),
                Map.of("stepId", "s-2", "type", "LLM")
        ));

        List<Map<String, Object>> steps = runtimeContext.getSteps();
        assertEquals(2, steps.size());
        assertEquals("s-1", steps.get(0).get("stepId"));
        assertEquals("s-2", steps.get(1).get("stepId"));
    }
}

