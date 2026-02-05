package com.example.agent.capabilities.tools;

import com.example.agent.capabilities.tools.registry.ToolCache;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.example.agent.capabilities.tools.mcp.McpToolDefinition;
import com.example.agent.capabilities.tools.mcp.McpToolSyncService;

/**
 * 默认工具目录实现。
 */
@Service
public class DefaultToolCatalog implements ToolCatalog, ToolCatalogService {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolCatalog.class);

    private final ToolRegistry toolRegistry;
    private final ToolCache toolCache;
    private final ObjectMapper objectMapper;
    private final MetricsPublisher metricsPublisher;
    private final ObjectProvider<McpToolSyncService> toolSyncServiceProvider;

    @Value("${agent.tool.definition-cache-ttl-seconds:300}")
    private long definitionCacheTtlSeconds;

    @Value("${agent.tool.schema-cache-ttl-seconds:300}")
    private long schemaCacheTtlSeconds;

    public DefaultToolCatalog(ToolRegistry toolRegistry,
                              ToolCache toolCache,
                              ObjectMapper objectMapper,
                              MetricsPublisher metricsPublisher,
                              ObjectProvider<McpToolSyncService> toolSyncServiceProvider) {
        this.toolRegistry = toolRegistry;
        this.toolCache = toolCache;
        this.objectMapper = objectMapper;
        this.metricsPublisher = metricsPublisher;
        this.toolSyncServiceProvider = toolSyncServiceProvider;
    }

    @Override
    public List<ToolSummary> listSummaries(ToolQuery query) {
        refreshRemoteTools("list_summaries");
        List<McpToolDefinition> definitions = toolRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<ToolSummary> summaries = new ArrayList<>();
        for (McpToolDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getName())) {
                continue;
            }
            if (!matchesTags(definition, query)) {
                continue;
            }
            ToolSummary summary = new ToolSummary();
            summary.setToolName(definition.getName());
            summary.setDescription(definition.getDescription());
            summary.setTags(definition.getTags());
            summaries.add(summary);
        }
        return summaries;
    }

    @Override
    public List<ToolSummary> listToolSummaries(ToolQuery query) {
        return listSummaries(query);
    }

    @Override
    public McpToolDefinition getDefinition(String toolName) {
        refreshRemoteTools("get_definition");
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        String cacheKey = "tool:def:" + toolName;
        Object cached = toolCache.getIfFresh(cacheKey, Duration.ofSeconds(Math.max(1, definitionCacheTtlSeconds)));
        McpToolDefinition fromCache = convertCached(cached);
        if (fromCache != null) {
            return fromCache;
        }
        McpToolDefinition definition = toolRegistry.getDefinition(toolName);
        if (definition != null) {
            toolCache.put(cacheKey, definition, Duration.ofSeconds(Math.max(1, definitionCacheTtlSeconds)));
        }
        return definition;
    }

    @Override
    public Map<String, Object> getToolSchema(String toolName) {
        refreshRemoteTools("get_schema");
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        String cacheKey = "tool:schema:" + toolName;
        Duration ttl = Duration.ofSeconds(Math.max(1, schemaCacheTtlSeconds));
        Object cached = toolCache.getIfFresh(cacheKey, ttl);
        Map<String, Object> schema = convertSchemaCached(cached);
        if (schema != null) {
            if (metricsPublisher != null) {
                metricsPublisher.increment("schema_cache_hit");
            }
            log.info("工具输入结构缓存命中, tool={}", toolName);
            return schema;
        }
        McpToolDefinition definition = toolRegistry.getDefinition(toolName);
        if (definition == null || definition.getInputSchema() == null || definition.getInputSchema().isEmpty()) {
            log.info("工具输入结构不存在, tool={}", toolName);
            return null;
        }
        schema = definition.getInputSchema();
        toolCache.put(cacheKey, schema, ttl);
        if (metricsPublisher != null) {
            metricsPublisher.increment("schema_loaded");
        }
        log.info("工具输入结构加载完成, tool={}", toolName);
        return schema;
    }

    private void refreshRemoteTools(String reason) {
        if (toolSyncServiceProvider == null) {
            return;
        }
        McpToolSyncService syncService = toolSyncServiceProvider.getIfAvailable();
        if (syncService != null) {
            syncService.refreshIfNeeded(reason);
        }
    }

    private boolean matchesTags(McpToolDefinition definition, ToolQuery query) {
        if (query == null || query.getRequiredTags() == null || query.getRequiredTags().isEmpty()) {
            return true;
        }
        if (definition.getTags() == null || definition.getTags().isEmpty()) {
            return false;
        }
        for (String required : query.getRequiredTags()) {
            if (!definition.getTags().contains(required)) {
                return false;
            }
        }
        return true;
    }

    private McpToolDefinition convertCached(Object cached) {
        if (cached instanceof McpToolDefinition definition) {
            return definition;
        }
        if (cached instanceof Map<?, ?> map) {
            try {
                return objectMapper.convertValue(map, McpToolDefinition.class);
            } catch (IllegalArgumentException ex) {
                log.warn("工具缓存转换失败");
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertSchemaCached(Object cached) {
        if (cached instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
