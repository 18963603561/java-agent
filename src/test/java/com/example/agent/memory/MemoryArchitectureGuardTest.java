package com.example.agent.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * memory 模块分层守护测试。
 * <p>用途：防止 support、repository、vector 等基础层出现对 recall/store 的反向依赖。
 */
class MemoryArchitectureGuardTest {

    private static final Path MEMORY_ROOT = Path.of("src/main/java/com/example/agent/capabilities/memory");
    private static final Path SUPPORT_ROOT = MEMORY_ROOT.resolve("support");
    private static final Path MEMORY_STORE_FILE = MEMORY_ROOT.resolve("MemoryStore.java");

    @Test
    void supportShouldNotDependOnStoreOrRecall() throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(SUPPORT_ROOT)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(SUPPORT_ROOT)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectForbiddenImport(path, violations,
                            "com.example.agent.capabilities.memory.store",
                            "com.example.agent.capabilities.memory.recall"));
        }
        assertTrue(violations.isEmpty(), "support 层不允许依赖 store/recall:\n" + String.join("\n", violations));
    }

    @Test
    void policyShouldNotDependOnStoreOrRecall() throws IOException {
        Path policyFile = MEMORY_ROOT.resolve("MemoryPolicy.java");
        if (!Files.exists(policyFile)) {
            return;
        }
        String source = Files.readString(policyFile, StandardCharsets.UTF_8);
        assertFalse(source.contains("com.example.agent.capabilities.memory.store"),
                "MemoryPolicy 不允许依赖 store 层");
        assertFalse(source.contains("com.example.agent.capabilities.memory.recall"),
                "MemoryPolicy 不允许依赖 recall 层");
    }

    @Test
    void repositoryAndVectorShouldNotDependOnRecall() throws IOException {
        List<Path> files = List.of(
                MEMORY_ROOT.resolve("repository/MemoryRepository.java"),
                MEMORY_ROOT.resolve("repository/InMemoryMemoryRepository.java"),
                MEMORY_ROOT.resolve("repository/JdbcMemoryRepository.java"),
                MEMORY_ROOT.resolve("vector/VectorStore.java"),
                MEMORY_ROOT.resolve("vector/QdrantVectorStore.java"),
                MEMORY_ROOT.resolve("vector/EmbeddingService.java"),
                MEMORY_ROOT.resolve("vector/HashEmbeddingService.java"),
                MEMORY_ROOT.resolve("config/MemoryVectorProperties.java")
        );
        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            if (!Files.exists(file)) {
                continue;
            }
            collectForbiddenImport(file, violations, "com.example.agent.capabilities.memory.recall");
        }
        assertTrue(violations.isEmpty(), "repository/vector 层不允许依赖 recall:\n" + String.join("\n", violations));
    }

    @Test
    void memoryStoreShouldRemainFacadeWithoutHeavyLogic() throws IOException {
        String source = Files.readString(MEMORY_STORE_FILE, StandardCharsets.UTF_8);
        assertTrue(source.contains("MemorySaveOrchestrator"), "MemoryStore 必须委派 MemorySaveOrchestrator");
        assertTrue(source.contains("MemorySearchOrchestrator"), "MemoryStore 必须委派 MemorySearchOrchestrator");
        assertTrue(source.contains("MemoryMaintenanceService"), "MemoryStore 必须委派 MemoryMaintenanceService");

        assertFalse(source.contains("memoryRepository.findBySession("), "MemoryStore 不应直接操作仓储查询");
        assertFalse(source.contains("vectorStoreProvider.getIfAvailable("), "MemoryStore 不应直接处理向量检索依赖");
        assertFalse(source.contains("embeddingServiceProvider.getIfAvailable("), "MemoryStore 不应直接处理嵌入依赖");
        assertFalse(source.contains("vectorStore.upsert("), "MemoryStore 不应直接处理向量写入逻辑");
        assertFalse(source.contains("deleteExpired("), "MemoryStore 不应直接执行过期清理");
        assertFalse(source.contains("autoCompressIfNeeded("), "MemoryStore 不应直接实现自动压缩细节");
    }

    /**
     * 收集文件中的禁止导入项。
     */
    private void collectForbiddenImport(Path file, List<String> violations, String... forbiddenImports) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            violations.add(file + " -> 读取失败: " + exception.getMessage());
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("import ")) {
                continue;
            }
            for (String forbiddenImport : forbiddenImports) {
                if (trimmed.contains(forbiddenImport)) {
                    violations.add(file + ":" + (index + 1) + " -> " + trimmed);
                }
            }
        }
    }
}
