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

class RuntimeSourceEncodingGuardTest {

    private static final List<String> BAD_MARKERS = List.of(
            "\u951F"
    );

    private static final Pattern PRIVATE_USE_CHAR_PATTERN = Pattern.compile("[\\uE000-\\uF8FF]");

    @Test
    void runtimePackageShouldNotContainEncodingMarkers() throws IOException {
        Path runtimeRoot = Paths.get("src/main/java/com/example/agent/runtime");
        List<String> violations = new ArrayList<>();

        List<Path> files;
        try (Stream<Path> stream = Files.walk(runtimeRoot)) {
            files = stream.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String marker : BAD_MARKERS) {
                if (content.contains(marker)) {
                    violations.add(file + " -> " + marker);
                }
            }
            if (PRIVATE_USE_CHAR_PATTERN.matcher(content).find()) {
                violations.add(file + " -> " + "private-use-char");
            }
        }

        assertTrue(violations.isEmpty(), "Detected suspicious encoding markers:\\n" + String.join("\\n", violations));
    }
}
