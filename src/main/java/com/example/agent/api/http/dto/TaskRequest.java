package com.example.agent.api.http.dto;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 浠诲姟鎻愪氦璇锋眰缁撴瀯銆?
 */
public class TaskRequest {

    /**
     * 鎵ц妯″紡鏋氫妇锛屽綋鍓嶄粎淇濈暀瀛楁銆?
     */
    public enum ExecutionMode {
        ASYNC,
        SYNC
    }

    /**
     * 浠诲姟鏌ヨ鍐呭銆?
     */
    @NotBlank(message = "鏌ヨ鍐呭涓嶈兘涓虹┖")
    private String query;

    /**
     * 浼氳瘽鏍囪瘑锛屽彲閫夈€?
     */
    private String sessionId;

    /**
     * 鎶€鑳藉悕绉帮紝鐢ㄤ簬闄愬畾鍙敤宸ュ叿涓庡伐鍏烽€夋嫨绛栫暐銆?
     */
    private String skillName;

    /**
     * 浠诲姟涓婁笅鏂囷紝鍙€夈€?
     */
    private Map<String, Object> context;

    /**
     * 骞傜瓑閿紙鍙€夛級銆?
     */
    private String idempotencyKey;

    /**
     * 宸ュ叿閫夋嫨绛栫暐锛屽彲閫夈€?
     */
    private ModelToolChoice toolChoice;

    /**
     * 鎵ц妯″紡锛屽彲閫夛紝榛樿鎸夊紓姝ュ鐞嗐€?
     */
    private ExecutionMode executionMode;

    /**
     * 鍚屾绛夊緟瓒呮椂鏃堕棿锛堟绉掞級锛屼粎鍦?SYNC 妯″紡鐢熸晥銆?
     */
    private Long waitTimeoutMs;

    public TaskRequest() {
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public ModelToolChoice getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(ModelToolChoice toolChoice) {
        this.toolChoice = toolChoice;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    public Long getWaitTimeoutMs() {
        return waitTimeoutMs;
    }

    public void setWaitTimeoutMs(Long waitTimeoutMs) {
        this.waitTimeoutMs = waitTimeoutMs;
    }
}

