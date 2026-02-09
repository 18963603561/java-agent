package com.example.agent.capabilities.context.builder.exception;

/**
 * 上下文构建异常，统一表达构建失败语义。
 */
public class ContextBuildException extends RuntimeException {

    /**
     * 失败编码。
     */
    private final String errorCode;

    /**
     * 失败阶段。
     */
    private final String stage;

    /**
     * 租户标识。
     */
    private final String tenantId;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 构造构建异常。
     *
     * @param message 异常描述
     * @param errorCode 失败编码
     * @param stage 失败阶段
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param cause 原始异常
     */
    public ContextBuildException(String message,
                                 String errorCode,
                                 String stage,
                                 String tenantId,
                                 String workflowId,
                                 Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.stage = stage;
        this.tenantId = tenantId;
        this.workflowId = workflowId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getStage() {
        return stage;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}

