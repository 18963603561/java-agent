package com.example.agent.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * llm 模块架构防回流守卫测试。
 * <p>用途：防止后续改动把实现类回流到根包，或在关键入口恢复旧协议与供应商分支逻辑。
 */
class LlmArchitectureGuardTest {

    private static final Path LLM_ROOT = Path.of("src/main/java/com/example/agent/capabilities/llm");
    private static final Path MODEL_INVOCATION_SERVICE =
            Path.of("src/main/java/com/example/agent/capabilities/llm/client/ModelInvocationService.java");

    private static final Set<String> ALLOWED_LLM_SUB_PACKAGES = Set.of(
            "client",
            "config",
            "prompt",
            "provider",
            "repair",
            "support",
            "tooling"
    );

    @Test
    void llmRootPackageShouldNotContainConcreteJavaFiles() throws IOException {
        List<Path> directJavaFiles;
        try (Stream<Path> stream = Files.list(LLM_ROOT)) {
            directJavaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        }

        assertTrue(directJavaFiles.isEmpty(),
                "llm 根包不允许放置实现类: " + directJavaFiles);
    }

    @Test
    void importsShouldNotReferenceLegacyLlmRootPackage() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(Path.of("src/main/java/com/example/agent"))) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectLegacyImportViolations(path, violations));
        }

        assertTrue(violations.isEmpty(), "检测到 llm 旧根包导入回流:\n" + String.join("\n", violations));
    }

    @Test
    void modelInvocationServiceShouldNotReintroduceLegacyOutputOrProviderBranches() throws IOException {
        String source = Files.readString(MODEL_INVOCATION_SERVICE, StandardCharsets.UTF_8);

        assertFalse(source.contains("publishOutputEvent("), "不允许恢复重复输出事件路径 publishOutputEvent");
        assertFalse(source.contains("EventType.LLM_OUTPUT"), "不允许在 ModelInvocationService 恢复 LLM_OUTPUT 事件发布");
        assertFalse(source.contains("switch (provider)"), "不允许在 ModelInvocationService 按 provider 分支处理错误");
        assertFalse(source.contains("if (provider =="), "不允许在 ModelInvocationService 按 provider 分支处理错误");
        assertFalse(source.contains("provider.equals("), "不允许在 ModelInvocationService 按 provider 分支处理错误");
    }

    @Test
    void executionProtocolClassesShouldNotUseMapAsResultCarrier() throws IOException {
        assertFalse(containsResultMapCarrier(
                Path.of("src/main/java/com/example/agent/capabilities/llm/client/LlmExecutionResult.java")),
                "LlmExecutionResult 不允许回流 Map 协议载体");
        assertFalse(containsResultMapCarrier(
                Path.of("src/main/java/com/example/agent/capabilities/llm/client/LlmExecutionFailure.java")),
                "LlmExecutionFailure 不允许回流 Map 协议载体");
        assertFalse(containsResultMapCarrier(
                Path.of("src/main/java/com/example/agent/capabilities/llm/client/LlmExecutionUsage.java")),
                "LlmExecutionUsage 不允许回流 Map 协议载体");
    }

    private void collectLegacyImportViolations(Path file, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            violations.add(file + " -> 读取文件失败: " + exception.getMessage());
            return;
        }

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("import com.example.agent.capabilities.llm.")) {
                continue;
            }

            String importPath = trimmed.substring("import com.example.agent.capabilities.llm.".length())
                    .replace(";", "")
                    .trim();
            String[] segments = importPath.split("\\.");
            if (segments.length == 0 || !ALLOWED_LLM_SUB_PACKAGES.contains(segments[0])) {
                violations.add(file + ":" + (index + 1) + " -> " + trimmed);
            }
        }
    }

    private boolean containsResultMapCarrier(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        return source.contains("Map<String, Object>") || source.contains("Map< String, Object >");
    }
}

