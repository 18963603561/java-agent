package com.example.agent.context;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextBuildRequest;
import com.example.agent.capabilities.context.model.ContextPolicy;
import com.example.agent.capabilities.context.builder.policy.ContextPolicyResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextPolicyResolverTest {

    private final ContextPolicyResolver resolver = new ContextPolicyResolver();

    @Test
    void resolveReturnsRequestPolicyFirst() {
        ContextBuildRequest request = new ContextBuildRequest();
        ContextPolicy requestPolicy = new ContextPolicy();
        requestPolicy.setPolicyId("request-policy");
        request.setPolicy(requestPolicy);

        ContextPolicy runtimePolicy = new ContextPolicy();
        runtimePolicy.setPolicyId("runtime-policy");

        ContextPolicy resolved = resolver.resolve(request, Map.of("contextPolicy", runtimePolicy));

        assertNotNull(resolved);
        assertEquals("request-policy", resolved.getPolicyId());
    }

    @Test
    void resolveBuildsPolicyFromMap() {
        ContextBuildRequest request = new ContextBuildRequest();

        ContextPolicy resolved = resolver.resolve(request, Map.of(
                "contextPolicy", Map.of(
                        "policyId", "p1",
                        "retrievalPriority", List.of("RECENT", "DOMAIN"),
                        "pruneOrder", List.of("WORKING_MEMORY"),
                        "maxEvidenceCount", "8",
                        "maxMemoryCount", 5,
                        "enableSensitiveMask", "true"
                )));

        assertNotNull(resolved);
        assertEquals("p1", resolved.getPolicyId());
        assertEquals(List.of("RECENT", "DOMAIN"), resolved.getRetrievalPriority());
        assertEquals(List.of("WORKING_MEMORY"), resolved.getPruneOrder());
        assertEquals(8, resolved.getMaxEvidenceCount());
        assertEquals(5, resolved.getMaxMemoryCount());
        assertEquals(Boolean.TRUE, resolved.getEnableSensitiveMask());
    }

    @Test
    void resolveFallsBackToTaskContextPolicy() {
        ContextBuildRequest request = new ContextBuildRequest();
        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setContext(Map.of("policy", Map.of("policyId", "task-policy")));
        request.setTaskRequest(taskRequest);

        ContextPolicy resolved = resolver.resolve(request, Map.of());

        assertNotNull(resolved);
        assertEquals("task-policy", resolved.getPolicyId());
    }

    @Test
    void resolveReturnsNullWhenPolicyMapHasNoContent() {
        ContextBuildRequest request = new ContextBuildRequest();

        ContextPolicy resolved = resolver.resolve(request, Map.of("contextPolicy", Map.of()));

        assertNull(resolved);
    }
}

