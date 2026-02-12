package com.example.agent.capabilities.context.compression.experiment.domain.model;

/**
 * 自动回滚决策结果。
 */
public class RollbackDecision {

    /**
     * 是否触发回滚。
     */
    private boolean rollback;

    /**
     * 回滚原因编码。
     */
    private String reason;

    /**
     * 建议胜出来源。
     */
    private String winnerSource;

    public boolean isRollback() {
        return rollback;
    }

    public void setRollback(boolean rollback) {
        this.rollback = rollback;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getWinnerSource() {
        return winnerSource;
    }

    public void setWinnerSource(String winnerSource) {
        this.winnerSource = winnerSource;
    }
}
