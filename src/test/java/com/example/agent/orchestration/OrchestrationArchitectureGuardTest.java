package com.example.agent.orchestration;

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
 * orchestration 包分层依赖守护测试。
 * <p>用途：防止 orchestration.task 回归依赖 api.http.dto。
 */
class OrchestrationArchitectureGuardTest {

    private static final String ORCHESTRATION_TASK_ROOT = "src/main/java/com/example/agent/orchestration/task";
    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "import com.example.agent.api.http.dto."
    );

    private static final List<String> MULTI_AGENT_PLACEHOLDER_FILES = List.of(
            "AgentGraph.java",
            "AgentGraphExecutor.java",
            "HandoffService.java",
            "HandoffRequest.java",
            "HandoffResult.java",
            "HandoffRecord.java"
    );

    @Test
    void orchestrationTaskShouldNotDependOnApiHttpDto() throws IOException {
        Path root = Path.of(ORCHESTRATION_TASK_ROOT);
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectImportViolations(path, FORBIDDEN_IMPORTS, violations));
        }
        assertTrue(violations.isEmpty(), "orchestration.task 检测到越级依赖:\n" + String.join("\n", violations));
    }

    @Test
    void multiAgentShouldNotContainPlaceholderObjects() {
        Path root = Path.of("src/main/java/com/example/agent/orchestration/multiagent");
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return;
        }
        for (String fileName : MULTI_AGENT_PLACEHOLDER_FILES) {
            Path candidate = root.resolve(fileName);
            if (Files.exists(candidate)) {
                violations.add(candidate.toString());
            }
        }
        assertTrue(violations.isEmpty(), "multiagent 检测到占位类未清理:\n" + String.join("\n", violations));
    }

    private void collectImportViolations(Path file, List<String> forbiddenImports, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            violations.add(file + " -> 读取文件失败:" + exception.getMessage());
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            for (String forbidden : forbiddenImports) {
                if (trimmed.startsWith(forbidden)) {
                    violations.add(file + ":" + (index + 1) + " -> " + trimmed);
                }
            }
        }
    }
}
