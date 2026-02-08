package com.example.agent.memory;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.config.MemoryRecallProperties;
import com.example.agent.capabilities.memory.recall.RecallContext;
import com.example.agent.capabilities.memory.recall.RecallContextResolver;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecallContextResolverTest {

    @Test
    void resolveShouldUseExplicitContextOverrides() {
        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setEnabled(true);
        properties.setLimit(5);
        properties.setMinQueryLength(3);
        properties.setMaxSummaryChars(400);
        properties.setMaxRecordChars(200);
        properties.setIncludeCompressed(false);
        RecallContextResolver resolver = new RecallContextResolver(properties);

        Map<String, Object> context = new HashMap<>();
        context.put("workflowId", "wf-explicit");
        context.put(RecallContextResolver.CONTEXT_RECALL_ENABLED, "false");
        context.put(RecallContextResolver.CONTEXT_RECALL_FORCE, true);
        context.put(RecallContextResolver.CONTEXT_RECALL_MIN_QUERY_LENGTH, "7");
        context.put(RecallContextResolver.CONTEXT_RECALL_LIMIT, 12);
        context.put(RecallContextResolver.CONTEXT_RECALL_MAX_SUMMARY_CHARS, "500");
        context.put(RecallContextResolver.CONTEXT_RECALL_MAX_RECORD_CHARS, 300);
        context.put(RecallContextResolver.CONTEXT_RECALL_INCLUDE_COMPRESSED, "true");

        TaskRequest request = new TaskRequest();
        request.setContext(Map.of("workflowId", "wf-request"));

        RecallContext resolved = resolver.resolve(request, context);
        assertEquals("wf-explicit", resolved.getWorkflowId());
        assertFalse(resolved.isEnabled());
        assertTrue(resolved.isForce());
        assertEquals(7, resolved.getMinQueryLength());
        assertEquals(12, resolved.getLimit());
        assertEquals(500, resolved.getMaxSummaryChars());
        assertEquals(300, resolved.getMaxRecordChars());
        assertTrue(resolved.isIncludeCompressed());
    }

    @Test
    void resolveShouldFallbackToRequestContextAndDefaults() {
        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setEnabled(true);
        properties.setLimit(6);
        properties.setMinQueryLength(2);
        properties.setMaxSummaryChars(600);
        properties.setMaxRecordChars(250);
        properties.setIncludeCompressed(true);
        RecallContextResolver resolver = new RecallContextResolver(properties);

        TaskRequest request = new TaskRequest();
        request.setContext(Map.of(
                "workflowId", "wf-request",
                RecallContextResolver.CONTEXT_RECALL_LIMIT, "invalid"
        ));

        RecallContext resolved = resolver.resolve(request, null);
        assertEquals("wf-request", resolved.getWorkflowId());
        assertTrue(resolved.isEnabled());
        assertFalse(resolved.isForce());
        assertEquals(2, resolved.getMinQueryLength());
        assertEquals(6, resolved.getLimit());
        assertEquals(600, resolved.getMaxSummaryChars());
        assertEquals(250, resolved.getMaxRecordChars());
        assertTrue(resolved.isIncludeCompressed());
    }
}

