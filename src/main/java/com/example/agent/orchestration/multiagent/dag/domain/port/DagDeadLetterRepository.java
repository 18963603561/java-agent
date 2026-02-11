package com.example.agent.orchestration.multiagent.dag.domain.port;

import com.example.agent.orchestration.multiagent.dag.actor.recovery.DagDeadLetterMessage;
import java.util.List;
import java.util.Optional;

/**
 * DAG 死信仓储端口。
 * <p>用途：记录不可消费消息并支持运维查询与重放。</p>
 */
public interface DagDeadLetterRepository {

    void save(DagDeadLetterMessage message);

    List<DagDeadLetterMessage> findByDagRun(String dagRunId);

    Optional<DagDeadLetterMessage> findById(String deadLetterId);

    void deleteById(String deadLetterId);
}

