package com.example.agent.reasoning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 辩论协调器，负责辩论流程控制。
 */
@Service
public class DebateCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DebateCoordinator.class);

    public DebateRound debate(String topic) {
        log.info("辩论开始, topic={}", topic);
        DebateRound round = new DebateRound();
        round.setTopic(topic);
        round.setConclusion("pending");
        return round;
    }
}
