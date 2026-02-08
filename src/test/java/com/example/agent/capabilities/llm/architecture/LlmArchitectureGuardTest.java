package com.example.agent.capabilities.llm.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * llm 模块架构防回流守卫测试。
 * <p>用途：防止后续改动把实现类回流到根包，或在关键入口恢复旧协议与供应商分支逻辑。
 */
class LlmArchitectureGuardTest {

    private static final Path LLM_ROOT = Path.of("src/main/java/com/example/agent/capabilities/llm");
    private static final Path LLM_TEST_ROOT = Path.of("src/test/java/com/example/agent/capabilities/llm");
    private static final Path LEGACY_MODEL_TEST_DIR = Path.of("src/test/java/com/example/agent/model");
    private static final Path MODEL_INVOCATION_SERVICE =
            Path.of("src/main/java/com/example/agent/capabilities/llm/client/ModelInvocationService.java");

    private static final Set<String> ALLOWED_LLM_SUB_PACKAGES = Set.of(
            "client",
            "contract",
            "config",
            "prompt",
            "provider",
            "repair",
            "support",
            "tooling"
    );

    private static final Set<String> ALLOWED_LLM_TEST_SUB_PACKAGES = Set.of(
            "architecture",
            "client",
            "prompt",
            "provider",
            "repair",
            "tooling"
    );

    private static final List<TestNameRule> TEST_NAME_RULES = List.of(
            rule("Architecture", "architecture"),
            rule("Prompt", "prompt"),
            rule("Repair", "repair"),
            rule("Tooling|ModelTool", "tooling"),
            rule("Provider|ModelRouter|ModelFallback|LocalFallback|Ollama|OpenAi|ToolPayload", "provider"),
            rule("Invocation|LlmEvent|LlmClient|RawRef|Failure", "client")
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

    @Test
    void llmTestsShouldNotStayInLegacyModelPackage() throws IOException {
        if (!Files.exists(LEGACY_MODEL_TEST_DIR)) {
            return;
        }
        List<Path> violations;
        try (Stream<Path> stream = Files.walk(LEGACY_MODEL_TEST_DIR)) {
            violations = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::isLlmRelatedTestFile)
                    .toList();
        }
        assertTrue(violations.isEmpty(), "检测到 LLM 测试回流到 model 目录:\n" + joinPaths(violations));
    }

    @Test
    void llmTestDirectoryShouldFollowDomainLayout() throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(LLM_TEST_ROOT)) {
            violations.add("缺少 llm 测试根目录: " + LLM_TEST_ROOT);
        } else {
            try (Stream<Path> stream = Files.walk(LLM_TEST_ROOT)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> collectLlmTestLayoutViolations(path, violations));
            }
        }
        assertTrue(violations.isEmpty(), "检测到 llm 测试目录违规:\n" + String.join("\n", violations));
    }

    @Test
    void llmTestNameShouldMatchDomainDirectory() throws IOException {
        if (!Files.exists(LLM_TEST_ROOT)) {
            return;
        }
        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(LLM_TEST_ROOT)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectTestNameLayoutViolations(path, violations));
        }
        assertTrue(violations.isEmpty(), "检测到 llm 测试命名与目录不一致:\n" + String.join("\n", violations));
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

    private void collectLlmTestLayoutViolations(Path file, List<String> violations) {
        Path relative;
        try {
            relative = LLM_TEST_ROOT.relativize(file);
        } catch (Exception exception) {
            violations.add(file + " -> 相对路径计算失败: " + exception.getMessage());
            return;
        }
        if (relative.getNameCount() < 2) {
            violations.add(file + " -> 不允许放在 llm 测试根目录");
            return;
        }
        String topPackage = relative.getName(0).toString();
        if (!ALLOWED_LLM_TEST_SUB_PACKAGES.contains(topPackage)) {
            violations.add(file + " -> 非法测试子目录: " + topPackage);
            return;
        }
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            violations.add(file + " -> 读取失败: " + exception.getMessage());
            return;
        }
        if (!source.contains("package com.example.agent.capabilities.llm.")) {
            violations.add(file + " -> 包声明未对齐 llm 测试目录");
        }
    }

    private void collectTestNameLayoutViolations(Path file, List<String> violations) {
        Path relative;
        try {
            relative = LLM_TEST_ROOT.relativize(file);
        } catch (Exception exception) {
            violations.add(file + " -> 相对路径计算失败: " + exception.getMessage());
            return;
        }
        if (relative.getNameCount() < 2) {
            return;
        }
        String topPackage = relative.getName(0).toString();
        String fileName = file.getFileName().toString();
        String baseName = fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
        for (TestNameRule rule : TEST_NAME_RULES) {
            if (!rule.pattern().matcher(baseName).find()) {
                continue;
            }
            if (!rule.expectedDir().equals(topPackage)) {
                violations.add(file + " -> 期望目录=" + rule.expectedDir() + ", 实际目录=" + topPackage);
            }
            return;
        }
    }

    private boolean isLlmRelatedTestFile(Path file) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return false;
        }
        return source.contains("com.example.agent.capabilities.llm") || source.contains("Llm");
    }

    private String joinPaths(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        List<String> values = paths.stream().map(Path::toString).toList();
        return String.join("\n", values);
    }

    private static TestNameRule rule(String keywordRegex, String expectedDir) {
        return new TestNameRule(Pattern.compile(keywordRegex, Pattern.CASE_INSENSITIVE), expectedDir);
    }

    private record TestNameRule(Pattern pattern, String expectedDir) {
    }
}
