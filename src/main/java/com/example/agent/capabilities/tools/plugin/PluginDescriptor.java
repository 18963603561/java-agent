package com.example.agent.capabilities.tools.plugin;

import java.util.List;

/**
 * 插件描述信息，声明插件名称、版本与工具定义文件。
 */
public class PluginDescriptor {

    /**
     * 插件名称。
     */
    private String name;
    /**
     * 插件版本。
     */
    private String version;
    /**
     * 工具定义文件列表，路径相对插件目录。
     */
    private List<String> toolsFiles;

    public PluginDescriptor() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getToolsFiles() {
        return toolsFiles;
    }

    public void setToolsFiles(List<String> toolsFiles) {
        this.toolsFiles = toolsFiles;
    }
}
