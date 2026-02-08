package com.example.agent.tools.registry;

import com.example.agent.capabilities.tools.mcp.McpToolDefinition;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryConcurrencyTest {

    @Test
    void registerAndReadConcurrentlyKeepsConsistentDefinitions() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                final int index = i;
                tasks.add(() -> {
                    registry.registerDefinitions(List.of(buildTool("tool-" + index)), "source-a", true);
                    assertNotNull(registry.getDefinition("tool-" + index));
                    registry.listDefinitions();
                    return null;
                });
            }
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Set<String> names = new HashSet<>();
        registry.listDefinitions().forEach(item -> names.add(item.getName()));
        for (int i = 0; i < 200; i++) {
            assertTrue(names.contains("tool-" + i));
        }
    }

    @Test
    void removeBySourceExceptUnderConcurrencyKeepsExpectedNames() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        for (int i = 0; i < 100; i++) {
            registry.registerDefinitions(List.of(buildTool("x-tool-" + i)), "source-x", true);
        }

        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            tasks.add(() -> {
                registry.removeDefinitionsBySourceExcept("source-x", Set.of("x-tool-1", "x-tool-3", "x-tool-5"));
                return null;
            });
            for (int i = 0; i < 100; i++) {
                final int index = i;
                tasks.add(() -> {
                    registry.getDefinition("x-tool-" + index);
                    registry.listDefinitions();
                    return null;
                });
            }

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Set<String> remain = new HashSet<>();
        registry.listDefinitions().forEach(item -> remain.add(item.getName()));
        assertTrue(remain.contains("x-tool-1"));
        assertTrue(remain.contains("x-tool-3"));
        assertTrue(remain.contains("x-tool-5"));
        assertEquals(false, remain.contains("x-tool-2"));
        assertEquals(false, remain.contains("x-tool-4"));
    }

    private McpToolDefinition buildTool(String name) {
        return new McpToolDefinition(
                name,
                "v1",
                "test-tool",
                Map.of("type", "object"),
                Map.of("type", "object"),
                List.of("test"));
    }
}

