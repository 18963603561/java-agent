package com.example.agent.budget;

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
 * budget 包分层依赖守卫测试。
 */
class BudgetArchitectureGuardTest {

    private static final String TOKEN_ROOT = "src/main/java/com/example/agent/budget/token";
    private static final String TRIM_ROOT = "src/main/java/com/example/agent/budget/trim";
    private static final String COST_ROOT = "src/main/java/com/example/agent/budget/cost";
    private static final String PRICING_ROOT = "src/main/java/com/example/agent/budget/token/pricing";
    private static final String TOKEN_MANAGER_FILE =
            "src/main/java/com/example/agent/budget/token/application/TokenBudgetManager.java";
    private static final String COMPRESSION_SERVICE_FILE =
            "src/main/java/com/example/agent/budget/trim/application/ContextCompressionService.java";

    private static final List<String> TOKEN_FORBIDDEN_IMPORTS = List.of(
            "import com.example.agent.budget.trim."
    );

    private static final List<String> TRIM_FORBIDDEN_IMPORTS = List.of(
            "import com.example.agent.budget.token."
    );

    @Test
    void tokenPackageShouldNotDependOnTrimPackage() throws IOException {
        List<String> violations = collectViolations(Path.of(TOKEN_ROOT), TOKEN_FORBIDDEN_IMPORTS);
        assertTrue(violations.isEmpty(), "budget.token 检测到对 budget.trim 的依赖:\n" + String.join("\n", violations));
    }

    @Test
    void trimPackageShouldNotDependOnTokenCoreModels() throws IOException {
        List<String> violations = collectViolations(Path.of(TRIM_ROOT), TRIM_FORBIDDEN_IMPORTS);
        assertTrue(violations.isEmpty(), "budget.trim 检测到对 budget.token 核心模型依赖:\n" + String.join("\n", violations));
    }

    @Test
    void tokenBudgetManagerShouldNotDirectlyDependOnLowLevelDetails() throws IOException {
        Path file = Path.of(TOKEN_MANAGER_FILE);
        List<String> forbidden = List.of(
                "import com.example.agent.streaming.domain.StreamEvent;",
                "import com.example.agent.streaming.domain.EventType;",
                "import com.example.agent.streaming.sse.EventStreamService;",
                "import com.example.agent.budget.token.application.DefaultBudgetThresholdEvaluator;",
                "private TokenUsageRecord buildRecord(",
                "private void publishThresholdEvent(",
                "private void publishFallbackEvent("
        );
        List<String> violations = collectFileViolations(file, forbidden);
        assertTrue(violations.isEmpty(), "TokenBudgetManager 存在未拆分职责实现:\n" + String.join("\n", violations));
    }

    @Test
    void compressionServiceShouldNotEmbedExecutionDetails() throws IOException {
        Path file = Path.of(COMPRESSION_SERVICE_FILE);
        List<String> forbidden = List.of(
                "import com.example.agent.capabilities.memory.MemoryStore;",
                "import com.example.agent.capabilities.memory.model.CompressionRequest;",
                "private String applyCompressedSummary(",
                "private String resolveTriggerReason(",
                "private boolean isInCooldown("
        );
        List<String> violations = collectFileViolations(file, forbidden);
        assertTrue(violations.isEmpty(), "ContextCompressionService 存在未拆分职责实现:\n" + String.join("\n", violations));
    }

    @Test
    void budgetFlowShouldNotUseNullAllocationAsControlSignal() throws IOException {
        List<String> targetFiles = List.of(
                "src/main/java/com/example/agent/budget/trim/application/ContextCompressionService.java",
                "src/main/java/com/example/agent/capabilities/context/compression/domain/policy/DefaultCompressionTriggerPolicy.java",
                "src/main/java/com/example/agent/budget/trim/application/DefaultContextPruner.java",
                "src/main/java/com/example/agent/capabilities/llm/prompt/PromptTrimEngine.java",
                "src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java"
        );
        List<String> forbiddenSnippets = List.of(
                "allocation == null",
                "getAllocation() == null"
        );
        List<String> violations = new ArrayList<>();
        for (String filePath : targetFiles) {
            violations.addAll(collectFileViolations(Path.of(filePath), forbiddenSnippets));
        }
        assertTrue(violations.isEmpty(), "预算主链路存在 null 预算控制分支:\n" + String.join("\n", violations));
    }

    @Test
    void trimPackageShouldNotContainControllerSuffixForServiceRole() throws IOException {
        Path root = Path.of(TRIM_ROOT);
        List<String> violations = new ArrayList<>();
        if (Files.exists(root)) {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(path -> path.toString().endsWith("Controller.java"))
                        .forEach(path -> violations.add(path.toString()));
            }
        }
        assertTrue(violations.isEmpty(), "budget.trim 存在命名不一致的 Controller 类:\n" + String.join("\n", violations));
    }

    @Test
    void budgetCostPackageShouldNotContainJavaSources() throws IOException {
        Path costRoot = Path.of(COST_ROOT);
        List<String> violations = new ArrayList<>();
        if (Files.exists(costRoot)) {
            violations.add(costRoot.toString());
        }
        assertTrue(violations.isEmpty(), "budget.cost 历史目录不应回流:\n" + String.join("\n", violations));
    }

    @Test
    void tokenAndTrimRootShouldNotContainDirectJavaSources() throws IOException {
        List<String> violations = new ArrayList<>();
        violations.addAll(collectDirectJavaFiles(Path.of(TOKEN_ROOT)));
        violations.addAll(collectDirectJavaFiles(Path.of(TRIM_ROOT)));
        assertTrue(violations.isEmpty(), "budget.token 或 budget.trim 根层不应直接放置 Java 源码:\n"
                + String.join("\n", violations));
    }

    @Test
    void pricingPackageShouldNotDependOnTrimImplementations() throws IOException {
        List<String> forbidden = List.of(
                "import com.example.agent.budget.trim.",
                "import com.example.agent.capabilities.context."
        );
        List<String> violations = collectViolations(Path.of(PRICING_ROOT), forbidden);
        assertTrue(violations.isEmpty(), "budget.token.pricing 出现越界依赖:\n" + String.join("\n", violations));
    }

    private List<String> collectFileViolations(Path file, List<String> forbiddenLines) throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(file)) {
            return violations;
        }
        scanFile(file, forbiddenLines, violations);
        return violations;
    }

    private List<String> collectViolations(Path root, List<String> forbiddenImports) throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return violations;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> scanFile(path, forbiddenImports, violations));
        }
        return violations;
    }

    private List<String> collectDirectJavaFiles(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return violations;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> violations.add(path.toString()));
        }
        return violations;
    }

    private void scanFile(Path file, List<String> forbiddenImports, List<String> violations) {
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
                if (trimmed.startsWith(forbidden) || trimmed.contains(forbidden)) {
                    violations.add(file + ":" + (index + 1) + " -> " + trimmed);
                }
            }
        }
    }
}

