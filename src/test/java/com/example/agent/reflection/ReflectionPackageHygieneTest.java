package com.example.agent.reflection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionPackageHygieneTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java");
    private static final Path TEST_ROOT = Paths.get("src/test/java");
    private static final Path REFLECTION_ROOT = MAIN_ROOT.resolve("com/example/agent/reflection");

    @Test
    void runtimeTypesUnderReflectionPackageMustBeConsumedByMainFlow() throws IOException {
        List<String> violations = new ArrayList<>();
        List<Path> reflectionFiles = Files.walk(REFLECTION_ROOT)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .toList();

        for (Path file : reflectionFiles) {
            String className = file.getFileName().toString().replace(".java", "");
            int mainReferences = countTokenInJavaTree(MAIN_ROOT, className);
            int testReferences = Files.exists(TEST_ROOT) ? countTokenInJavaTree(TEST_ROOT, className) : 0;
            if (mainReferences <= 1 && testReferences > 0) {
                violations.add(className + " -> 仅测试使用，未进入主链路");
                continue;
            }
            if (mainReferences <= 1) {
                violations.add(className + " -> 未被主代码消费");
            }
        }

        assertTrue(violations.isEmpty(), "reflection 包存在孤立运行时对象: " + violations);
    }

    private int countTokenInJavaTree(Path root, String token) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(token) + "\\b");
        int total = 0;
        for (Path file : Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .toList()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                total++;
            }
        }
        return total;
    }
}

