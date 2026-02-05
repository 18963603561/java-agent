package com.example.agent.orchestration.multiagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 监督者协调器，负责角色分配与调度。
 */
@Service
public class SupervisorCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SupervisorCoordinator.class);

    private final MultiAgentCoordinator multiAgentCoordinator;

    public SupervisorCoordinator(MultiAgentCoordinator multiAgentCoordinator) {
        this.multiAgentCoordinator = multiAgentCoordinator;
    }

    public void supervise(String taskId) {
        log.info("监督者调度, taskId={}", taskId);
        multiAgentCoordinator.coordinate(taskId);
    }
}
