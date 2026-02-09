package com.example.agent.governance;

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
 * governance 包分层依赖守卫测试。
 */
class GovernanceArchitectureGuardTest {

    private static final String GOVERNANCE_ROOT = "src/main/java/com/example/agent/governance";
    private static final String API_CONTROLLER_ROOT = "src/main/java/com/example/agent/api/http/controller";

    private static final List<String> GOVERNANCE_FORBIDDEN_IMPORTS = List.of(
            "import com.example.agent.api.http.dto.",
            "import com.example.agent.api.http.controller."
    );

    @Test
    void governancePackageShouldNotDependOnApiDtoOrController() throws IOException {
        Path root = Path.of(GOVERNANCE_ROOT);
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectImportViolations(path, GOVERNANCE_FORBIDDEN_IMPORTS, violations));
        }
        assertTrue(violations.isEmpty(), "governance 检测到越级依赖:\n" + String.join("\n", violations));
    }

    @Test
    void governancePackageShouldNotContainHttpDtoClassName() throws IOException {
        Path root = Path.of(GOVERNANCE_ROOT);
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        if (fileName.endsWith("Request.java") || fileName.endsWith("Response.java")) {
                            violations.add(path + " -> 命名包含 HTTP DTO 后缀");
                        }
                    });
        }
        assertTrue(violations.isEmpty(), "governance 检测到 DTO 命名泄漏:\n" + String.join("\n", violations));
    }

    @Test
    void replayControllerShouldOnlyDependOnReplayApiDto() throws IOException {
        Path file = Path.of(API_CONTROLLER_ROOT, "ReplayController.java");
        if (!Files.exists(file)) {
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("import ")) {
                continue;
            }
            if (!trimmed.contains("com.example.agent.governance.replay.")) {
                continue;
            }
            if (trimmed.contains("com.example.agent.governance.replay.ReplayService")) {
                continue;
            }
            if (trimmed.contains("com.example.agent.governance.replay.domain.ReplayCommand")) {
                continue;
            }
            if (trimmed.contains("com.example.agent.governance.replay.domain.ReplayResult")) {
                continue;
            }
            violations.add(file + ":" + (index + 1) + " -> " + trimmed);
        }
        assertTrue(violations.isEmpty(), "ReplayController 检测到越级依赖:\n" + String.join("\n", violations));
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

