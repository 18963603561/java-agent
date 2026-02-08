package com.example.agent.tools.plugin;

import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.capabilities.tools.model.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.agent.capabilities.tools.plugin.PluginDescriptor;
import com.example.agent.capabilities.tools.plugin.PluginLoader;
import com.example.agent.capabilities.tools.plugin.PluginProperties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

        ToolDefinition tool = new ToolDefinition();
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

    @Test
    void loaderIgnoresPathTraversalToolFile() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Path pluginDir = pluginsDir.resolve("safe-plugin");
        Files.createDirectories(pluginDir);

        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setName("safe-plugin");
        descriptor.setVersion("1.0.0");
        descriptor.setToolsFiles(List.of("../outside-tools.json"));

        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(pluginDir.resolve("plugin.json").toFile(), descriptor);

        ToolDefinition outsideTool = new ToolDefinition();
        outsideTool.setName("outside_tool");
        outsideTool.setVersion("v1");
        outsideTool.setDescription("outside tool");
        outsideTool.setInputSchema(Map.of("type", "object"));
        outsideTool.setOutputSchema(Map.of("type", "object"));
        outsideTool.setTags(List.of("outside"));
        mapper.writeValue(pluginsDir.resolve("outside-tools.json").toFile(), List.of(outsideTool));

        PluginProperties properties = new PluginProperties();
        properties.setDir(pluginsDir.toString());
        ToolRegistry toolRegistry = new ToolRegistry();
        PluginLoader loader = new PluginLoader(properties, toolRegistry, mapper);

        loader.loadPlugins();

        assertFalse(toolRegistry.listDefinitions().stream()
                .anyMatch(definition -> "outside_tool".equals(definition.getName())));
    }
}
