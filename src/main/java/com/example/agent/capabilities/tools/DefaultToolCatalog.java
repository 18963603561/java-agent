package com.example.agent.capabilities.tools;

import com.example.agent.capabilities.tools.registry.ToolCache;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.capabilities.tools.model.ToolDefinition;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.example.agent.capabilities.tools.mcp.McpToolSyncService;

/**
 * 默认工具目录实现。
 */
@Service
public class DefaultToolCatalog implements ToolCatalogService {

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
        List<ToolDefinition> definitions = toolRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<ToolSummary> summaries = new ArrayList<>();
        for (ToolDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getName())) {
                continue;
            }
            if (!matchesTags(definition, query)) {
                continue;
            }
            if (!matchesScopes(definition, query)) {
                continue;
            }
            if (!matchesLocale(definition, query)) {
                continue;
            }
            ToolSummary summary = new ToolSummary();
            summary.setToolName(definition.getName());
            summary.setDescription(definition.getDescription());
            summary.setTags(definition.getTags());
            summary.setAuthScope(definition.getAuthScope());
            summaries.add(summary);
        }
        return summaries;
    }

    @Override
    public ToolDefinition getDefinition(String toolName) {
        refreshRemoteTools("get_definition");
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        String cacheKey = "tool:def:" + toolName;
        Object cached = toolCache.getIfFresh(cacheKey, Duration.ofSeconds(Math.max(1, definitionCacheTtlSeconds)));
        ToolDefinition fromCache = convertCached(cached);
        if (fromCache != null) {
            return fromCache;
        }
        ToolDefinition definition = toolRegistry.getDefinition(toolName);
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
        ToolDefinition definition = toolRegistry.getDefinition(toolName);
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

    private boolean matchesTags(ToolDefinition definition, ToolQuery query) {
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

    /**
     * 按授权范围匹配工具。
     *
     * <p>规则：当查询未指定范围时不过滤；指定后仅命中相同范围的工具。</p>
     *
     * @param definition 工具定义
     * @param query 查询条件
     * @return 是否命中范围规则
     */
    private boolean matchesScopes(ToolDefinition definition, ToolQuery query) {
        if (query == null || query.getAllowedScopes() == null || query.getAllowedScopes().isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(definition.getAuthScope())) {
            return false;
        }
        for (String allowedScope : query.getAllowedScopes()) {
            if (StringUtils.hasText(allowedScope)
                    && definition.getAuthScope().equalsIgnoreCase(allowedScope.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按语言区域匹配工具。
     *
     * <p>规则：当查询未指定语言时不过滤；指定后需要命中支持列表。</p>
     *
     * @param definition 工具定义
     * @param query 查询条件
     * @return 是否命中语言规则
     */
    private boolean matchesLocale(ToolDefinition definition, ToolQuery query) {
        if (query == null || !StringUtils.hasText(query.getLocale())) {
            return true;
        }
        if (definition.getSupportedLocales() == null || definition.getSupportedLocales().isEmpty()) {
            return false;
        }
        String expectedLocale = normalizeLocale(query.getLocale());
        for (String supportedLocale : definition.getSupportedLocales()) {
            if (!StringUtils.hasText(supportedLocale)) {
                continue;
            }
            if (expectedLocale.equals(normalizeLocale(supportedLocale))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 归一化语言区域字符串，统一匹配格式。
     *
     * @param locale 原始语言区域
     * @return 归一化结果
     */
    private String normalizeLocale(String locale) {
        return locale.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private ToolDefinition convertCached(Object cached) {
        if (cached instanceof ToolDefinition definition) {
            return definition;
        }
        if (cached instanceof Map<?, ?> map) {
            try {
                return objectMapper.convertValue(map, ToolDefinition.class);
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
            try {
                Map<String, Object> converted = objectMapper.convertValue(cached, Map.class);
                return converted != null ? converted : null;
            } catch (IllegalArgumentException ex) {
                log.warn("工具结构缓存转换失败");
                return null;
            }
        }
        return null;
    }
}
