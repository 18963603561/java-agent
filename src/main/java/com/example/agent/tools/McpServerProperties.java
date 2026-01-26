package com.example.agent.tools;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP 服务配置项。
 */
@Component
@ConfigurationProperties(prefix = "agent.mcp")
public class McpServerProperties {

    private List<McpServer> servers = new ArrayList<>();

    public List<McpServer> getServers() {
        return servers;
    }

    public void setServers(List<McpServer> servers) {
        this.servers = servers;
    }

    /**
     * MCP 服务定义。
     */
    public static class McpServer {

        private String id;
        private boolean available = true;
        private String baseUrl;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
