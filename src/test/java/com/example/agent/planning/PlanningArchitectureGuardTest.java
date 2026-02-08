package com.example.agent.planning;

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
 * planning 包分层依赖守卫测试。
 *
 * <p>用途：约束 planning 分层方向，避免跨层越级依赖再次出现。
 */
class PlanningArchitectureGuardTest {

    private static final String STRATEGY_ROOT = "src/main/java/com/example/agent/planning/strategy";
    private static final String CAPABILITY_ROOT = "src/main/java/com/example/agent/planning/capability";

    private static final List<String> STRATEGY_FORBIDDEN_IMPORTS = List.of(
            "import com.example.agent.planning.engine.",
            "import com.example.agent.planning.telemetry.",
            "import com.example.agent.planning.approval.",
            "import com.example.agent.planning.capability."
    );

    private static final List<String> CAPABILITY_FORBIDDEN_IMPORTS = List.of(
            "import com.example.agent.planning.engine.",
            "import com.example.agent.planning.telemetry."
    );

    @Test
    void strategyPackageShouldNotDependOnEngineOrTelemetry() throws IOException {
        Path root = Path.of(STRATEGY_ROOT);
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectViolations(path, STRATEGY_FORBIDDEN_IMPORTS, violations));
        }
        assertTrue(violations.isEmpty(), "planning.strategy 检测到越级依赖:\n" + String.join("\n", violations));
    }

    @Test
    void capabilityPackageShouldNotDependOnEngineOrTelemetry() throws IOException {
        Path root = Path.of(CAPABILITY_ROOT);
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectViolations(path, CAPABILITY_FORBIDDEN_IMPORTS, violations));
        }
        assertTrue(violations.isEmpty(), "planning.capability 检测到越级依赖:\n" + String.join("\n", violations));
    }

    private void collectViolations(Path file, List<String> forbiddenImports, List<String> violations) {
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
