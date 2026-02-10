package com.example.agent.reasoning.common.result;

import java.util.HashMap;
import java.util.Map;

/**
 * Debate 强类型结果载荷。
 *
 * <p>用途：定义辩论策略的固定输出字段，避免动态键访问风险。
 */
public class DebatePayload implements ReasoningPayload {

    private final String roundId;
    private final String topic;
    private final String conclusion;

    /**
     * 构造 Debate 载荷。
     *
     * @param roundId 轮次标识
     * @param topic 辩论主题
     * @param conclusion 结论
     */
    public DebatePayload(String roundId, String topic, String conclusion) {
        this.roundId = roundId;
        this.topic = topic;
        this.conclusion = conclusion;
    }

    public String getRoundId() {
        return roundId;
    }

    public String getTopic() {
        return topic;
    }

    public String getConclusion() {
        return conclusion;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("roundId", roundId);
        map.put("topic", topic);
        map.put("conclusion", conclusion);
        return map;
    }
}

