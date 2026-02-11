package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import java.util.List;

/**
 * DAG 分布式邮箱传输接口。
 *
 * <p>用途：抽象消息发送、拉取、确认与重投递，支持替换不同外置通道。</p>
 */
public interface DagMailboxTransport {

    /**
     * 发送消息。
     */
    void send(DagMessageEnvelope envelope);

    /**
     * 拉取某实例可消费的消息。
     */
    List<DagMessageEnvelope> poll(String instanceId, int batchSize);

    /**
     * 确认消息已完成处理。
     */
    void ack(String envelopeId, String instanceId);

    /**
     * 拒绝消息并按退避重投递。
     */
    void nack(String envelopeId, String instanceId, long backoffMs, String reason);

    /**
     * 查询指定运行的待消费消息。
     */
    List<DagMessageEnvelope> listPendingByDagRun(String dagRunId);
}

