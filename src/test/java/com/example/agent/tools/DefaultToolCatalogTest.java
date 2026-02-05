package com.example.agent.tools;

import com.example.agent.capabilities.tools.registry.ToolCache;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import com.example.agent.capabilities.tools.DefaultToolCatalog;
import com.example.agent.capabilities.tools.mcp.McpToolDefinition;
import com.example.agent.capabilities.tools.mcp.McpToolSyncService;
import com.example.agent.capabilities.tools.ToolQuery;
import com.example.agent.capabilities.tools.ToolSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultToolCatalogTest {

    @Test
    void listToolSummariesReturnsRegisteredTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerDefinitions(List.of(
                new McpToolDefinition("tool_a", "v1", "a",
                        Map.of("type", "object"), Map.of("type", "object"), List.of("demo"))
        ));

        ToolCache cache = Mockito.mock(ToolCache.class);
        when(cache.getIfFresh(any(), any())).thenReturn(null);

        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ObjectProvider<McpToolSyncService> provider = Mockito.mock(ObjectProvider.class);

        DefaultToolCatalog catalog = new DefaultToolCatalog(registry, cache, new ObjectMapper(),
                metricsPublisher, provider);

        List<ToolSummary> summaries = catalog.listToolSummaries(new ToolQuery());
        assertTrue(summaries.stream().anyMatch(summary -> "tool_a".equals(summary.getToolName())));

        Map<String, Object> schema = catalog.getToolSchema("tool_a");
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        verify(metricsPublisher).increment("schema_loaded");
    }

    @Test
    void getToolSchemaUsesCacheWhenAvailable() {
        ToolRegistry registry = Mockito.mock(ToolRegistry.class);
        ToolCache cache = Mockito.mock(ToolCache.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ObjectProvider<McpToolSyncService> provider = Mockito.mock(ObjectProvider.class);

        Map<String, Object> cachedSchema = Map.of("type", "object");
        when(cache.getIfFresh(eq("tool:schema:tool_a"), any())).thenReturn(cachedSchema);

        DefaultToolCatalog catalog = new DefaultToolCatalog(registry, cache, new ObjectMapper(),
                metricsPublisher, provider);

        Map<String, Object> schema = catalog.getToolSchema("tool_a");
        assertEquals(cachedSchema, schema);
        verify(registry, never()).getDefinition(anyString());
        verify(metricsPublisher).increment("schema_cache_hit");
    }

    @Test
    void getToolSchemaReturnsNullForUnknownTool() {
        ToolRegistry registry = Mockito.mock(ToolRegistry.class);
        ToolCache cache = Mockito.mock(ToolCache.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ObjectProvider<McpToolSyncService> provider = Mockito.mock(ObjectProvider.class);

        when(cache.getIfFresh(any(), any())).thenReturn(null);
        when(registry.getDefinition("unknown")).thenReturn(null);

        DefaultToolCatalog catalog = new DefaultToolCatalog(registry, cache, new ObjectMapper(),
                metricsPublisher, provider);

        assertNull(catalog.getToolSchema("unknown"));
        verify(metricsPublisher, never()).increment("schema_loaded");
    }
}
