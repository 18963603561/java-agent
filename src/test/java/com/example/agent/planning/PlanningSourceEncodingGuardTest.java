package com.example.agent.planning;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * planning 包源码编码守卫测试。
 *
 * <p>用途：阻止 planning 包再次出现私有区字符导致的乱码回归。
 */
class PlanningSourceEncodingGuardTest {

    private static final Pattern PRIVATE_USE_CHAR_PATTERN = Pattern.compile("[\\uE000-\\uF8FF]");

    @Test
    void planningPackageShouldNotContainPrivateUseChars() throws IOException {
        Path planningRoot = Paths.get("src/main/java/com/example/agent/planning");
        List<String> violations = new ArrayList<>();

        List<Path> files;
        try (Stream<Path> stream = Files.walk(planningRoot)) {
            files = stream.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (PRIVATE_USE_CHAR_PATTERN.matcher(content).find()) {
                violations.add(file.toString());
            }
        }

        assertTrue(violations.isEmpty(), "Detected private-use chars in planning package:\n" + String.join("\n", violations));
    }
}

