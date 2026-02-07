package com.example.agent.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 运行时关键 Map 键访问守门测试。
 *
 * <p>用途：阻止在非权威点新增散落的 {@code Map.get("xxx")} / {@code containsKey("xxx")} 访问，避免输出契约漂移。
 * <p>说明：该测试只扫描源码（{@code src/main/java}），不对运行时行为产生影响。
 */
class MapKeyAccessGuardTest {

    private static final List<String> SCAN_DIRS = List.of(
            "src/main/java/com/example/agent/runtime",
            "src/main/java/com/example/agent/reflection",
            "src/main/java/com/example/agent/capabilities"
    );

    private static final List<String> KEYWORDS = List.of(
            "rawRef",
            "refs",
            "toolName",
            "outputSummary",
            "outputDigest",
            "stepSummary",
            "toolResultSummary",
            "inputSummary",
            "inputDigest",
            "requiresApproval",
            "approvalSource",
            "lastStepId",
            "lastStepType",
            "lastStepSummary",
            "lastStepRawOutput",
            "lastStepRawRef",
            "lastStepRawRefs",
            "lastStepRawTruncated"
    );

    private static final List<String> METHODS = List.of(
            "get",
            "containsKey"
    );

    /**
     * 权威读取点白名单。
     *
     * <p>说明：这些类负责边界解包或上下文规范化，允许出现受控的键访问。
     */
    private static final Map<String, List<String>> AUTHORITY_KEY_WHITELIST = Map.of(
            "src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java",
            List.of("requiresApproval", "approvalSource"),
            "src/main/java/com/example/agent/runtime/model/input/ApprovalInput.java",
            List.of("requiresApproval", "approvalSource"),
            "src/main/java/com/example/agent/runtime/model/input/LastStepInput.java",
            List.of("lastStepId", "lastStepType", "lastStepSummary", "lastStepRawOutput",
                    "lastStepRawRef", "lastStepRawRefs", "lastStepRawTruncated"),
            "src/main/java/com/example/agent/runtime/step/RuntimeContext.java",
            List.of("requiresApproval", "approvalSource"),
            "src/main/java/com/example/agent/runtime/llm/LlmDecisionService.java",
            List.of("lastStepSummary"),
            "src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java",
            List.of("requiresApproval")
    );

    @Test
    void noScatteredKeyAccessForRuntimeContractFields() throws Exception {
        List<GuardPattern> patterns = buildPatterns();
        List<String> violations = new ArrayList<>();
        for (String dir : SCAN_DIRS) {
            Path root = Path.of(dir);
            if (!Files.exists(root)) {
                continue;
            }
            scanJavaFiles(root, patterns, violations);
        }
        if (!violations.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            builder.append("发现运行时关键键的散落访问点（请收口到权威点/常量层）：\n");
            int limit = Math.min(violations.size(), 200);
            for (int i = 0; i < limit; i++) {
                builder.append(violations.get(i)).append('\n');
            }
            if (violations.size() > limit) {
                builder.append("... 省略 ").append(violations.size() - limit).append(" 条\n");
            }
            fail(builder.toString());
        }
    }

    private static List<GuardPattern> buildPatterns() {
        List<GuardPattern> patterns = new ArrayList<>();
        for (String method : METHODS) {
            for (String key : KEYWORDS) {
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(method) + "\\(\\s*\\\"" + Pattern.quote(key) + "\\\"\\s*\\)");
                patterns.add(new GuardPattern(method, key, pattern));
            }
        }
        return patterns;
    }

    private static void scanJavaFiles(Path root, List<GuardPattern> patterns, List<String> violations) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file == null || !file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    for (GuardPattern pattern : patterns) {
                        if (pattern.pattern.matcher(line).find()) {
                            if (isWhitelisted(file, pattern.key)) {
                                continue;
                            }
                            violations.add(file + ":" + (i + 1) + " [" + pattern.method + ":" + pattern.key + "] " + line.trim());
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isWhitelisted(Path file, String key) {
        if (file == null || key == null) {
            return false;
        }
        String normalized = file.toString().replace('\\', '/');
        for (Map.Entry<String, List<String>> entry : AUTHORITY_KEY_WHITELIST.entrySet()) {
            if (!normalized.endsWith(entry.getKey())) {
                continue;
            }
            List<String> keys = entry.getValue();
            return keys != null && keys.contains(key);
        }
        return false;
    }

    private record GuardPattern(String method, String key, Pattern pattern) {
    }
}
