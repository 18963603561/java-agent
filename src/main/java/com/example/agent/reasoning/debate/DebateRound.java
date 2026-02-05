package com.example.agent.reasoning.debate;

/**
 * 辩论轮次记录。
 */
public class DebateRound {

    private String roundId;
    private String topic;
    private String conclusion;
    /**
     * 辩论原始输出引用。
     */
    private String rawRef;

    public DebateRound() {
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }
}
