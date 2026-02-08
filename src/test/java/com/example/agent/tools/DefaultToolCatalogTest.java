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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultToolCatalogTest {

    @Test
    void listSummariesReturnsRegisteredTools() {
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

        List<ToolSummary> summaries = catalog.listSummaries(new ToolQuery());
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

    @Test
    void listSummariesFiltersByAllowedScopes() {
        ToolRegistry registry = new ToolRegistry();
        McpToolDefinition readTool = new McpToolDefinition("tool_read", "v1", "read",
                Map.of("type", "object"), Map.of("type", "object"), List.of("demo"));
        readTool.setAuthScope("scope.read");
        McpToolDefinition writeTool = new McpToolDefinition("tool_write", "v1", "write",
                Map.of("type", "object"), Map.of("type", "object"), List.of("demo"));
        writeTool.setAuthScope("scope.write");
        registry.registerDefinitions(List.of(readTool, writeTool), "test", true);

        ToolCache cache = Mockito.mock(ToolCache.class);
        when(cache.getIfFresh(any(), any())).thenReturn(null);

        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ObjectProvider<McpToolSyncService> provider = Mockito.mock(ObjectProvider.class);
        DefaultToolCatalog catalog = new DefaultToolCatalog(registry, cache, new ObjectMapper(),
                metricsPublisher, provider);

        ToolQuery query = new ToolQuery();
        query.setAllowedScopes(List.of("scope.read"));
        List<ToolSummary> summaries = catalog.listSummaries(query);

        assertTrue(summaries.stream().anyMatch(item -> "tool_read".equals(item.getToolName())));
        assertFalse(summaries.stream().anyMatch(item -> "tool_write".equals(item.getToolName())));
        assertTrue(summaries.stream().allMatch(item -> "scope.read".equals(item.getAuthScope())));
    }

    @Test
    void listSummariesFiltersByLocale() {
        ToolRegistry registry = new ToolRegistry();
        McpToolDefinition zhTool = new McpToolDefinition("tool_zh", "v1", "zh",
                Map.of("type", "object"), Map.of("type", "object"), List.of("demo"));
        zhTool.setSupportedLocales(List.of("zh-CN", "en-US"));
        McpToolDefinition enTool = new McpToolDefinition("tool_en", "v1", "en",
                Map.of("type", "object"), Map.of("type", "object"), List.of("demo"));
        enTool.setSupportedLocales(List.of("en-US"));
        registry.registerDefinitions(List.of(zhTool, enTool), "test", true);

        ToolCache cache = Mockito.mock(ToolCache.class);
        when(cache.getIfFresh(any(), any())).thenReturn(null);

        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ObjectProvider<McpToolSyncService> provider = Mockito.mock(ObjectProvider.class);
        DefaultToolCatalog catalog = new DefaultToolCatalog(registry, cache, new ObjectMapper(),
                metricsPublisher, provider);

        ToolQuery query = new ToolQuery();
        query.setLocale("zh_cn");
        List<ToolSummary> summaries = catalog.listSummaries(query);

        assertTrue(summaries.stream().anyMatch(item -> "tool_zh".equals(item.getToolName())));
        assertFalse(summaries.stream().anyMatch(item -> "tool_en".equals(item.getToolName())));
    }
}
