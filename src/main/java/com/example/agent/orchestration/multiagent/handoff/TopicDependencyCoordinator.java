package com.example.agent.orchestration.multiagent.handoff;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * topic 依赖协调器。
 *
 * <p>用途：在 Supervisor 路径下等待 consumes 依赖满足，支持超时控制。</p>
 */
@Component
public class TopicDependencyCoordinator {

    private static final Logger log = LoggerFactory.getLogger(TopicDependencyCoordinator.class);

    private final WorkspaceSyncService workspaceSyncService;
    private final long timeoutMs;
    private final long pollIntervalMs;

    public TopicDependencyCoordinator(WorkspaceSyncService workspaceSyncService,
                                      @Value("${agent.multiagent.supervisor.dependency-timeout-ms:60000}") long timeoutMs,
                                      @Value("${agent.multiagent.supervisor.dependency-poll-ms:100}") long pollIntervalMs) {
        this.workspaceSyncService = workspaceSyncService;
        this.timeoutMs = Math.max(10L, timeoutMs);
        this.pollIntervalMs = Math.max(10L, pollIntervalMs);
    }

    /**
     * 等待依赖 topic。
     *
     * @param workflowId 工作流标识
     * @param topics 依赖 topic 列表
     * @return 缺失 topic 列表，空表示全部满足
     */
    public List<String> awaitDependencies(String workflowId, List<String> topics) {
        List<String> requiredTopics = normalize(topics);
        if (requiredTopics.isEmpty()) {
            return List.of();
        }
        Instant deadline = Instant.now().plusMillis(timeoutMs);
        while (Instant.now().isBefore(deadline)) {
            List<String> missing = collectMissingTopics(workflowId, requiredTopics);
            if (missing.isEmpty()) {
                return List.of();
            }
            sleepQuietly(pollIntervalMs);
        }
        List<String> missing = collectMissingTopics(workflowId, requiredTopics);
        if (!missing.isEmpty()) {
            log.warn("依赖等待超时, workflowId={}, timeoutMs={}, missingTopics={}",
                    workflowId,
                    timeoutMs,
                    missing);
        }
        return missing;
    }

    /**
     * 检查依赖是否全部满足。
     */
    public boolean dependenciesSatisfied(String workflowId, List<String> topics) {
        List<String> missing = collectMissingTopics(workflowId, normalize(topics));
        return missing.isEmpty();
    }

    private List<String> collectMissingTopics(String workflowId, List<String> requiredTopics) {
        if (requiredTopics == null || requiredTopics.isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (String topic : requiredTopics) {
            if (!workspaceSyncService.hasTopic(workflowId, topic)) {
                missing.add(topic);
            }
        }
        return missing;
    }

    private List<String> normalize(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String topic : topics) {
            if (!StringUtils.hasText(topic)) {
                continue;
            }
            String value = topic.trim();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("依赖等待被中断", ex);
        }
    }
}

