package com.example.agent.orchestration.multiagent.architecture;

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
 * 多智能体可观测性字典守护测试。
 * <p>用途：防止关键链路重新引入指标名与标签键硬编码。</p>
 */
class MultiAgentObservabilityDictionaryGuardTest {

    private static final String MULTIAGENT_ROOT = "src/main/java/com/example/agent/orchestration/multiagent";
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "\"dag.recovery.detected\"",
            "\"dag.recovery.duration\"",
            "\"dag.recovery.deadletter.replayed\"",
            "\"dag.control.failed\"",
            "\"dag.control.success\"",
            "\"dag.control.resume.dispatched\"",
            "\"dag.control.rebalance.pending\"",
            "\"dag.mailbox.lag\"",
            "\"dag.dispatch.dlq\"",
            "\"dag.dispatch.nack\"",
            "\"dag.dispatch.ack\"",
            "\"dag.dispatch.sent\""
    );

    @Test
    void shouldNotUseMetricAndTagHardcodedLiteralOutsideDictionary() throws IOException {
        Path root = Path.of(MULTIAGENT_ROOT);
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("observability"))
                    .forEach(path -> collectMetricLiteralViolations(path, FORBIDDEN_TOKENS, violations));
        }
        assertTrue(violations.isEmpty(), "检测到可观测性硬编码字面量:\n" + String.join("\n", violations));
    }

    @Test
    void eventPublisherShouldNotUsePayloadPutLiteralKeys() throws IOException {
        Path eventRoot = Path.of(MULTIAGENT_ROOT, "event");
        List<String> violations = new ArrayList<>();
        if (!Files.exists(eventRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(eventRoot)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectEventPayloadLiteralViolations(path, violations));
        }
        assertTrue(violations.isEmpty(), "检测到事件载荷键硬编码:\n" + String.join("\n", violations));
    }

    private void collectMetricLiteralViolations(Path file,
                                                List<String> forbiddenTokens,
                                                List<String> violations) {
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
            for (String forbiddenToken : forbiddenTokens) {
                if (trimmed.contains(forbiddenToken)) {
                    violations.add(file + ":" + (index + 1) + " -> " + trimmed);
                }
            }
        }
    }

    private void collectEventPayloadLiteralViolations(Path file, List<String> violations) {
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
            if (trimmed.contains("payload.put(\"")) {
                violations.add(file + ":" + (index + 1) + " -> " + trimmed);
            }
        }
    }
}
