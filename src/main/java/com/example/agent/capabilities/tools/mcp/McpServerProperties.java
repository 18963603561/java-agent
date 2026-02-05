package com.example.agent.capabilities.tools.mcp;

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
        /**
         * MCP 调用协议类型，支持 rest 或 jsonrpc。
         */
        private String protocol = "rest";

        /**
         * SSE 会话地址，配置后自动获取 sessionId。
         */
        private String sseUrl;

        /**
         * SSE 会话参数名称。
         */
        private String sessionParamName = "sessionId";

        /**
         * SSE 会话刷新间隔秒数，0 表示不主动刷新。
         */
        private long sessionRefreshSeconds = 300;

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

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getSseUrl() {
            return sseUrl;
        }

        public void setSseUrl(String sseUrl) {
            this.sseUrl = sseUrl;
        }

        public String getSessionParamName() {
            return sessionParamName;
        }

        public void setSessionParamName(String sessionParamName) {
            this.sessionParamName = sessionParamName;
        }

        public long getSessionRefreshSeconds() {
            return sessionRefreshSeconds;
        }

        public void setSessionRefreshSeconds(long sessionRefreshSeconds) {
            this.sessionRefreshSeconds = sessionRefreshSeconds;
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
