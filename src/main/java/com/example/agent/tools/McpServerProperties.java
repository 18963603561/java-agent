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

        /**
         * 允许访问的目标主机列表，默认空表示拒绝远程调用。
         */
        private List<String> allowedHosts = new ArrayList<>();

        /**
         * 响应体最大字节数，默认 2 兆字节。
         */
        private long maxResponseBytes = 2 * 1024 * 1024L;

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

        public List<String> getAllowedHosts() {
            return allowedHosts;
        }

        public void setAllowedHosts(List<String> allowedHosts) {
            this.allowedHosts = allowedHosts;
        }

        public long getMaxResponseBytes() {
            return maxResponseBytes;
        }

        public void setMaxResponseBytes(long maxResponseBytes) {
            this.maxResponseBytes = maxResponseBytes;
        }
    }
}
