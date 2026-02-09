package com.example.agent.budget.trim.model;

/**
 * 裁剪统计信息，记录被移除的数量与体积。
 */
public class ContextTrimStats {

    /**
     * 被移除项数量。
     */
    private int removedCount;

    /**
     * 被移除的字符数估算。
     */
    private int removedChars;

    /**
     * 被移除的令牌数估算。
     */
    private int removedTokens;

    public int getRemovedCount() {
        return removedCount;
    }

    public void setRemovedCount(int removedCount) {
        this.removedCount = removedCount;
    }

    public int getRemovedChars() {
        return removedChars;
    }

    public void setRemovedChars(int removedChars) {
        this.removedChars = removedChars;
    }

    public int getRemovedTokens() {
        return removedTokens;
    }

    public void setRemovedTokens(int removedTokens) {
        this.removedTokens = removedTokens;
    }

    /**
     * 累加裁剪统计。
     *
     * @param count 移除数量
     * @param chars 移除字符数
     * @param tokens 移除令牌数
     */
    public void addRemoved(int count, int chars, int tokens) {
        this.removedCount += Math.max(0, count);
        this.removedChars += Math.max(0, chars);
        this.removedTokens += Math.max(0, tokens);
    }
}
