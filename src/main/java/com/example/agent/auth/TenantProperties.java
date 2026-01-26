package com.example.agent.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 租户过滤器相关配置。
 */
@Component
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    /**
     * 免租户校验的白名单路径。
     */
    private List<String> whitelistPaths = new ArrayList<>(List.of("/actuator/health", "/actuator/info"));

    public List<String> getWhitelistPaths() {
        return whitelistPaths;
    }

    public void setWhitelistPaths(List<String> whitelistPaths) {
        this.whitelistPaths = whitelistPaths;
    }
}
