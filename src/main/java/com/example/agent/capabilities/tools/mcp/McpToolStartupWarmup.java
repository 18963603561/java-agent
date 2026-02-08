package com.example.agent.capabilities.tools.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动后预热远程 MCP 工具清单，写入本地注册表。
 */
@Component
public class McpToolStartupWarmup {

    private static final Logger log = LoggerFactory.getLogger(McpToolStartupWarmup.class);

    private final McpToolSyncService toolSyncService;

    /**
     * 启动阶段是否执行预热。
     */
    @Value("${agent.mcp.tool-refresh.startup-enabled:true}")
    private boolean startupEnabled;

    public McpToolStartupWarmup(McpToolSyncService toolSyncService) {
        this.toolSyncService = toolSyncService;
    }

    /**
     * 应用就绪后强制拉取远程工具清单。
     * agent.mcp.remote-enabled=true
     * agent.mcp.tool-refresh.enabled=true
     * agent.mcp.servers[0].id=mcp-default
     * agent.mcp.servers[0].base-url=http://your-mcp-host
     * agent.mcp.servers[0].allowed-hosts[0]=your-mcp-host
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        if (!startupEnabled) {
            log.info("MCP 工具启动预热已关闭");
            return;
        }
        try {
            toolSyncService.refreshAll(true, "startup");
            log.info("MCP 工具启动预热完成");
        } catch (Exception ex) {
            // 启动预热失败不应阻断主流程
            log.warn("MCP 工具启动预热失败", ex);
        }
    }
}
