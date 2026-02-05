package com.example.agent.capabilities.tools.mcp;

import com.example.agent.capabilities.tools.registry.ToolCache;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * MCP 工具同步服务，用于加载远程工具并写入本地注册表。
 */
@Component
public class McpToolSyncService {

    private static final Logger log = LoggerFactory.getLogger(McpToolSyncService.class);
    private static final String CACHE_KEY_PREFIX = "mcp:tools:";
    private static final String SOURCE_PREFIX = "mcp:";

    private final McpToolClient mcpToolClient;
    private final ToolRegistry toolRegistry;
    private final ToolCache toolCache;
    private final McpServerProperties serverProperties;
    private final ObjectMapper objectMapper;

    /**
     * 是否启用远程工具缓存刷新。
     */
    @Value("${agent.mcp.tool-refresh.enabled:true}")
    private boolean refreshEnabled;

    /**
     * 缓存有效期（秒），小于等于 0 表示不强制过期。
     */
    @Value("${agent.mcp.tool-refresh.ttl-seconds:300}")
    private long refreshTtlSeconds;

    /**
     * 单次同步最大工具数量，避免异常返回导致内存压力。
     */
    @Value("${agent.mcp.tool-refresh.max-tools:2000}")
    private int maxTools;

    public McpToolSyncService(McpToolClient mcpToolClient,
                              ToolRegistry toolRegistry,
                              ToolCache toolCache,
                              McpServerProperties serverProperties,
                              ObjectMapper objectMapper) {
        this.mcpToolClient = mcpToolClient;
        this.toolRegistry = toolRegistry;
        this.toolCache = toolCache;
        this.serverProperties = serverProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 按需刷新远程工具列表。
     *
     * @param reason 触发原因
     */
    public void refreshIfNeeded(String reason) {
        refreshAll(false, reason);
    }

    /**
     * 刷新全部 MCP 服务器工具。
     *
     * @param force 是否强制刷新
     * @param reason 触发原因
     */
    public void refreshAll(boolean force, String reason) {
        if (!refreshEnabled) {
            return;
        }
        if (serverProperties == null || serverProperties.getServers() == null
                || serverProperties.getServers().isEmpty()) {
            return;
        }
        for (McpServerProperties.McpServer server : serverProperties.getServers()) {
            refreshServer(server, force, reason);
        }
    }

    private void refreshServer(McpServerProperties.McpServer server, boolean force, String reason) {
        if (server == null || !server.isAvailable()) {
            return;
        }
        String serverId = StringUtils.hasText(server.getId()) ? server.getId() : "mcp-default";
        // 优先读取缓存并写入注册表，避免首次访问时阻塞主流程。
        CachedToolList cached = readCache(serverId);
        if (cached != null && cached.tools != null && !cached.tools.isEmpty()) {
            applyToRegistry(serverId, cached.tools, "cache");
        }
        boolean cacheFresh = isCacheFresh(cached);
        if (!force && cacheFresh) {
            if (log.isDebugEnabled()) {
                log.debug("MCP 工具缓存命中, serverId={}, reason={}", serverId, reason);
            }
            return;
        }
        try {
            log.info("MCP 工具同步开始, serverId={}, reason={}", serverId, reason);
            McpToolListResponse response = mcpToolClient.listToolsRemoteOnly(
                    server,
                    new McpToolListRequest(),
                    buildSystemContext(serverId));
            List<McpToolDefinition> tools = response != null && response.getTools() != null
                    ? new ArrayList<>(response.getTools())
                    : new ArrayList<>();
            if (maxTools > 0 && tools.size() > maxTools) {
                tools = new ArrayList<>(tools.subList(0, maxTools));
                log.warn("MCP 工具数量超限, serverId={}, maxTools={}, trimmed={}",
                        serverId, maxTools, tools.size());
            }
            CachedToolList updated = new CachedToolList(System.currentTimeMillis(), tools);
            writeCache(serverId, updated);
            applyToRegistry(serverId, tools, "remote");
            log.info("MCP 工具同步完成, serverId={}, count={}", serverId, tools.size());
        } catch (ErrorCodeException ex) {
            log.warn("MCP 工具同步失败, serverId={}, reason={}, code={}",
                    serverId, reason, ex.getErrorCode(), ex);
        } catch (Exception ex) {
            log.error("MCP 工具同步异常, serverId={}, reason={}", serverId, reason, ex);
        }
    }

    private void applyToRegistry(String serverId, List<McpToolDefinition> tools, String source) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        String registrySource = SOURCE_PREFIX + serverId;
        // 先覆盖同源工具，再清理已下线的旧工具，避免注册表残留。
        toolRegistry.registerDefinitions(tools, registrySource, true);
        toolRegistry.removeDefinitionsBySourceExcept(registrySource, collectNames(tools));
        if (log.isDebugEnabled()) {
            log.debug("MCP 工具写入注册表完成, serverId={}, source={}, count={}",
                    serverId, source, tools.size());
        }
    }

    private Set<String> collectNames(List<McpToolDefinition> tools) {
        Set<String> names = new HashSet<>();
        if (tools == null) {
            return names;
        }
        for (McpToolDefinition tool : tools) {
            if (tool != null && StringUtils.hasText(tool.getName())) {
                names.add(tool.getName());
            }
        }
        return names;
    }

    private boolean isCacheFresh(CachedToolList cached) {
        if (cached == null) {
            return false;
        }
        if (refreshTtlSeconds <= 0) {
            return true;
        }
        long ageMs = System.currentTimeMillis() - cached.updatedAt;
        return ageMs >= 0 && ageMs < refreshTtlSeconds * 1000;
    }

    private CachedToolList readCache(String serverId) {
        String cacheKey = CACHE_KEY_PREFIX + serverId;
        Object cached = toolCache.get(cacheKey);
        if (cached instanceof CachedToolList list) {
            return list;
        }
        if (cached instanceof Map<?, ?> map) {
            try {
                return objectMapper.convertValue(map, CachedToolList.class);
            } catch (IllegalArgumentException ex) {
                log.warn("MCP 工具缓存转换失败, serverId={}", serverId, ex);
                return null;
            }
        }
        return null;
    }

    private void writeCache(String serverId, CachedToolList cached) {
        String cacheKey = CACHE_KEY_PREFIX + serverId;
        Duration ttl = refreshTtlSeconds > 0
                ? Duration.ofSeconds(refreshTtlSeconds)
                : Duration.ZERO;
        toolCache.put(cacheKey, cached, ttl);
    }

    private TenantContext buildSystemContext(String serverId) {
        return new TenantContext("system", "system", List.of("system"), "mcp-sync-" + serverId, null);
    }

    /**
     * MCP 工具缓存快照。
     */
    private static final class CachedToolList {
        /**
         * 缓存更新时间戳（毫秒）。
         */
        private long updatedAt;
        /**
         * 缓存的工具定义列表。
         */
        private List<McpToolDefinition> tools;

        private CachedToolList() {
        }

        private CachedToolList(long updatedAt, List<McpToolDefinition> tools) {
            this.updatedAt = updatedAt;
            this.tools = tools;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
        }

        public List<McpToolDefinition> getTools() {
            return tools;
        }

        public void setTools(List<McpToolDefinition> tools) {
            this.tools = tools;
        }
    }
}
