package com.example.agent.runtime;

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
 * llm 源码编码守卫测试。
 */
class LlmSourceEncodingGuardTest {

    private static final Pattern PRIVATE_USE_CHAR_PATTERN = Pattern.compile("[\\uE000-\\uF8FF]");

    @Test
    void llmPackageShouldNotContainPrivateUseChars() throws IOException {
        Path llmRoot = Paths.get("src/main/java/com/example/agent/capabilities/llm");
        List<String> violations = new ArrayList<>();

        List<Path> files;
        try (Stream<Path> stream = Files.walk(llmRoot)) {
            files = stream.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (PRIVATE_USE_CHAR_PATTERN.matcher(content).find()) {
                violations.add(file.toString());
            }
        }

        assertTrue(violations.isEmpty(), "Detected private-use chars in llm package:\n" + String.join("\n", violations));
    }
}

