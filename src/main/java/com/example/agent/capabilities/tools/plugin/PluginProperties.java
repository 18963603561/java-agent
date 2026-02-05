package com.example.agent.capabilities.tools.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 插件加载配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.plugin")
public class PluginProperties {

    /**
     * 是否启用插件加载。
     */
    private boolean enabled = true;
    /**
     * 插件目录，优先级高于 TOOLBOX_HOME。
     */
    private String dir;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }
}
