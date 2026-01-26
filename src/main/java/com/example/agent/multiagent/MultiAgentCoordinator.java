package com.example.agent.multiagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 多智能体协调器，负责协调并行与串行执行。
 */
@Service
public class MultiAgentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentCoordinator.class);

    public void coordinate(String taskId) {
        log.info("多智能体协调开始, taskId={}", taskId);
    }
}
