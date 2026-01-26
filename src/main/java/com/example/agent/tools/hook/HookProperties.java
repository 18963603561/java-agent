package com.example.agent.tools.hook;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hook 配置项。
 */
@Component
@ConfigurationProperties(prefix = "agent.hook")
public class HookProperties {

    private boolean enabled = true;
    private List<String> blockedTools = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBlockedTools() {
        return blockedTools;
    }

    public void setBlockedTools(List<String> blockedTools) {
        this.blockedTools = blockedTools;
    }
}
