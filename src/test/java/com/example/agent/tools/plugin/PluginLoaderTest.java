package com.example.agent.tools.plugin;

import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.tools.McpToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loaderRegistersToolDefinitions() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Path pluginDir = pluginsDir.resolve("demo-plugin");
        Files.createDirectories(pluginDir);

        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setName("demo-plugin");
        descriptor.setVersion("1.0.0");
        descriptor.setToolsFiles(List.of("tools.json"));

        ObjectMapper mapper = new ObjectMapper();
        Path descriptorPath = pluginDir.resolve("plugin.json");
        mapper.writeValue(descriptorPath.toFile(), descriptor);

        McpToolDefinition tool = new McpToolDefinition();
        tool.setName("plugin_tool");
        tool.setVersion("v1");
        tool.setDescription("plugin tool");
        tool.setInputSchema(Map.of("type", "object"));
        tool.setOutputSchema(Map.of("type", "object"));
        tool.setTags(List.of("plugin"));
        mapper.writeValue(pluginDir.resolve("tools.json").toFile(), List.of(tool));

        PluginProperties properties = new PluginProperties();
        properties.setDir(pluginsDir.toString());
        ToolRegistry toolRegistry = new ToolRegistry();
        PluginLoader loader = new PluginLoader(properties, toolRegistry, mapper);

        loader.loadPlugins();

        assertTrue(toolRegistry.listDefinitions().stream()
                .anyMatch(definition -> "plugin_tool".equals(definition.getName())));
    }
}
