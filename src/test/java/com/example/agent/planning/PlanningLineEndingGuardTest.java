package com.example.agent.planning;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * planning 范围行结束符守卫测试。
 *
 * <p>用途：阻止 planning 相关源码与提示词模板出现 CRLF 回归，统一为 LF。
 */
class PlanningLineEndingGuardTest {

    @Test
    void planningScopeShouldUseLfLineEnding() throws IOException {
        List<String> violations = new ArrayList<>();
        Set<Path> files = collectScanFiles();

        for (Path file : files) {
            byte[] bytes = Files.readAllBytes(file);
            if (containsCrLf(bytes)) {
                violations.add(file.toString());
            }
        }

        assertTrue(violations.isEmpty(),
                "Detected CRLF line ending in planning scope:\n" + String.join("\n", violations));
    }

    private Set<Path> collectScanFiles() throws IOException {
        Set<Path> files = new LinkedHashSet<>();
        collectFiles(files, Paths.get("src/main/java/com/example/agent/planning"), ".java");
        collectFiles(files, Paths.get("src/test/java/com/example/agent/planning"), ".java");
        collectFiles(files, Paths.get("src/main/resources/prompts/planning"), ".md");
        return files;
    }

    private void collectFiles(Set<Path> files, Path root, String suffix) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(suffix))
                    .forEach(files::add);
        }
    }

    private boolean containsCrLf(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            return false;
        }
        for (int index = 0; index < bytes.length - 1; index++) {
            if (bytes[index] == '\r' && bytes[index + 1] == '\n') {
                return true;
            }
        }
        return false;
    }
}

