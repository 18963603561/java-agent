package com.example.agent.runtime;

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
 * 摘要包依赖方向守门测试。
 *
 * <p>用途：约束 {@code summary} 包仅依赖契约与基础能力，禁止直接依赖 {@code step} 包实现细节。
 * <p>边界：该测试仅扫描源码 import 语句，不影响运行时行为。
 */
class SummaryDependencyGuardTest {

    private static final String SUMMARY_ROOT = "src/main/java/com/example/agent/runtime/summary";

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "import com.example.agent.runtime.step.",
            "import com.example.agent.runtime.output.",
            "import com.example.agent.runtime.engine.",
            "import com.example.agent.runtime.recovery."
    );

    @Test
    void summaryPackageShouldNotDependOnStepOrEngineInternals() throws IOException {
        Path root = Path.of(SUMMARY_ROOT);
        List<String> violations = new ArrayList<>();

        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(file -> collectViolations(file, violations));
        }

        assertTrue(violations.isEmpty(), "summary 包检测到越级依赖:\n" + String.join("\n", violations));
    }

    private void collectViolations(Path file, List<String> violations) {
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
            for (String forbidden : FORBIDDEN_IMPORTS) {
                if (trimmed.startsWith(forbidden)) {
                    violations.add(file + ":" + (index + 1) + " -> " + trimmed);
                }
            }
        }
    }
}
