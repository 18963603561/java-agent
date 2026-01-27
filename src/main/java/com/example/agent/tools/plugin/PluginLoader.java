package com.example.agent.tools.plugin;

import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.tools.McpToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 插件加载器，扫描插件目录并加载工具定义。
 */
@Component
public class PluginLoader {

    private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);
    private static final String DEFAULT_DESCRIPTOR = "plugin.json";

    private final PluginProperties pluginProperties;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public PluginLoader(PluginProperties pluginProperties,
                        ToolRegistry toolRegistry,
                        ObjectMapper objectMapper) {
        this.pluginProperties = pluginProperties;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadPlugins();
    }

    /**
     * 扫描插件目录并加载工具定义。
     */
    public void loadPlugins() {
        if (!pluginProperties.isEnabled()) {
            log.info("插件加载已禁用");
            return;
        }
        Path pluginDir = resolvePluginDir();
        if (pluginDir == null) {
            log.warn("插件目录为空, 跳过加载");
            return;
        }
        if (!Files.exists(pluginDir)) {
            log.info("插件目录不存在, dir={}", pluginDir);
            return;
        }
        if (!Files.isDirectory(pluginDir)) {
            log.warn("插件目录不是目录, dir={}", pluginDir);
            return;
        }
        log.info("开始加载插件, dir={}", pluginDir);
        try (Stream<Path> paths = Files.list(pluginDir)) {
            paths.filter(Files::isDirectory)
                    .forEach(this::loadPluginDirectory);
        } catch (Exception ex) {
            log.error("扫描插件目录失败, dir={}", pluginDir, ex);
        }
    }

    private void loadPluginDirectory(Path pluginPath) {
        Path descriptorPath = pluginPath.resolve(DEFAULT_DESCRIPTOR);
        if (!Files.exists(descriptorPath)) {
            log.debug("插件缺少 descriptor, path={}", pluginPath);
            return;
        }
        PluginDescriptor descriptor = readDescriptor(descriptorPath);
        if (descriptor == null) {
            log.warn("插件描述解析失败, path={}", descriptorPath);
            return;
        }
        List<McpToolDefinition> tools = new ArrayList<>();
        if (descriptor.getToolsFiles() != null) {
            for (String fileName : descriptor.getToolsFiles()) {
                if (!StringUtils.hasText(fileName)) {
                    continue;
                }
                Path toolPath = pluginPath.resolve(fileName);
                if (!Files.exists(toolPath)) {
                    log.warn("工具定义文件不存在, plugin={}, path={}", descriptor.getName(), toolPath);
                    continue;
                }
                tools.addAll(readTools(toolPath));
            }
        }
        if (!tools.isEmpty()) {
            toolRegistry.registerDefinitions(tools);
            log.info("插件工具加载完成, plugin={}, tools={}", descriptor.getName(), tools.size());
        }
    }

    private PluginDescriptor readDescriptor(Path descriptorPath) {
        try (InputStream input = Files.newInputStream(descriptorPath)) {
            return objectMapper.readValue(input, PluginDescriptor.class);
        } catch (Exception ex) {
            log.error("读取插件描述失败, path={}", descriptorPath, ex);
            return null;
        }
    }

    private List<McpToolDefinition> readTools(Path toolPath) {
        try (InputStream input = Files.newInputStream(toolPath)) {
            JsonNode node = objectMapper.readTree(input);
            if (node == null) {
                return List.of();
            }
            JsonNode toolsNode = node;
            if (!node.isArray()) {
                toolsNode = node.get("tools");
            }
            if (toolsNode == null) {
                return List.of();
            }
            List<McpToolDefinition> tools = new ArrayList<>();
            if (toolsNode.isArray()) {
                for (JsonNode item : toolsNode) {
                    tools.add(objectMapper.treeToValue(item, McpToolDefinition.class));
                }
            } else {
                tools.add(objectMapper.treeToValue(toolsNode, McpToolDefinition.class));
            }
            return tools;
        } catch (Exception ex) {
            log.error("读取工具定义失败, path={}", toolPath, ex);
            return List.of();
        }
    }

    private Path resolvePluginDir() {
        String dir = pluginProperties.getDir();
        if (StringUtils.hasText(dir)) {
            return Path.of(dir);
        }
        String toolboxHome = System.getenv("TOOLBOX_HOME");
        if (StringUtils.hasText(toolboxHome)) {
            return Path.of(toolboxHome, "plugins");
        }
        return Path.of("plugins");
    }
}
