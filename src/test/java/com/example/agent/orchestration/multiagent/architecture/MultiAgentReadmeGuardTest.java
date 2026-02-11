package com.example.agent.orchestration.multiagent.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * multiagent README 一致性守护测试。
 * <p>用途：保证文档声明与代码目录结构持续一致。</p>
 */
class MultiAgentReadmeGuardTest {

    @Test
    void readmeShouldContainLayerAndObservabilityRules() throws IOException {
        Path readme = Path.of("src/main/java/com/example/agent/orchestration/multiagent/README.md");
        assertTrue(Files.exists(readme), "multiagent README 不存在");
        String content = Files.readString(readme, StandardCharsets.UTF_8);
        assertTrue(content.contains("dag.domain.port"), "README 必须声明端口分层规则");
        assertTrue(content.contains("observability"), "README 必须声明可观测性字典规则");
        assertTrue(content.contains("评审检查清单"), "README 必须包含评审检查清单章节");
    }

    @Test
    void dagReadmeShouldMatchCurrentLayerDirectories() throws IOException {
        Path dagReadme = Path.of("src/main/java/com/example/agent/orchestration/multiagent/dag/README.md");
        assertTrue(Files.exists(dagReadme), "dag README 不存在");
        String content = Files.readString(dagReadme, StandardCharsets.UTF_8);
        List<Path> requiredDirs = List.of(
                Path.of("src/main/java/com/example/agent/orchestration/multiagent/dag/application"),
                Path.of("src/main/java/com/example/agent/orchestration/multiagent/dag/domain"),
                Path.of("src/main/java/com/example/agent/orchestration/multiagent/dag/infrastructure")
        );
        for (Path directory : requiredDirs) {
            assertTrue(Files.exists(directory), "dag 分层目录缺失: " + directory);
        }
        assertTrue(content.contains("dag.application"), "dag README 必须声明 application 分层");
        assertTrue(content.contains("dag.domain"), "dag README 必须声明 domain 分层");
        assertTrue(content.contains("dag.infrastructure"), "dag README 必须声明 infrastructure 分层");
    }
}

