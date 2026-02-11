package com.example.agent.orchestration.multiagent.dag.actor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * DAG Actor 消息定义。
 *
 * <p>用途：承载节点间通信上下文，支持依赖推进、状态变更与失败传播。</p>
 * <p>输入输出：输入由运行时或节点 Actor 构造，输出由目标 Actor 消费。</p>
 * <p>边界条件：messageId 为空时由构造器自动生成，不建议跨运行时复用实例。</p>
 */
public class DagMessage {

    private final DagMessageType type;
    private final String workflowId;
    private final String messageId;
    private final String fromNode;
    private final String toNode;
    private final String topic;
    private final long version;
    private final Instant timestamp;
    private final Map<String, Object> payload;

    public DagMessage(DagMessageType type,
                      String workflowId,
                      String fromNode,
                      String toNode,
                      String topic,
                      long version,
                      Map<String, Object> payload) {
        this(type,
                workflowId,
                buildMessageId(workflowId, fromNode, toNode, topic, version),
                fromNode,
                toNode,
                topic,
                version,
                Instant.now(),
                payload);
    }

    public DagMessage(DagMessageType type,
                      String workflowId,
                      String messageId,
                      String fromNode,
                      String toNode,
                      String topic,
                      long version,
                      Instant timestamp,
                      Map<String, Object> payload) {
        this.type = type;
        this.workflowId = workflowId;
        this.messageId = messageId;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.topic = topic;
        this.version = version;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.payload = payload == null ? Map.of() : Map.copyOf(new HashMap<>(payload));
    }

    /**
     * 构建消息幂等键。
     *
     * <p>规则：workflowId:fromNode:toNode:topic:version</p>
     */
    public static String buildMessageId(String workflowId,
                                        String fromNode,
                                        String toNode,
                                        String topic,
                                        long version) {
        String safeWorkflowId = workflowId == null ? "" : workflowId;
        String safeFromNode = fromNode == null ? "" : fromNode;
        String safeToNode = toNode == null ? "" : toNode;
        String safeTopic = topic == null ? "" : topic;
        return safeWorkflowId + ":" + safeFromNode + ":" + safeToNode + ":" + safeTopic + ":" + version;
    }

    public DagMessageType getType() {
        return type;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getFromNode() {
        return fromNode;
    }

    public String getToNode() {
        return toNode;
    }

    public String getTopic() {
        return topic;
    }

    public long getVersion() {
        return version;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}

