package com.example.agent.orchestration.multiagent.dag.actor.recovery;

import java.util.List;
import java.util.Optional;

/**
 * DAG 死信仓储接口。
 *
 * <p>用途：保存与查询死信消息，支撑补偿重放。</p>
 */
public interface DagDeadLetterRepository {

    /**
     * 保存死信记录。
     */
    void save(DagDeadLetterMessage message);

    /**
     * 查询某次运行下的死信记录。
     */
    List<DagDeadLetterMessage> findByDagRun(String dagRunId);

    /**
     * 按死信标识查询。
     */
    Optional<DagDeadLetterMessage> findById(String deadLetterId);

    /**
     * 按死信标识删除。
     */
    void deleteById(String deadLetterId);
}
