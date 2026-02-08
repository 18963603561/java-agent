package com.example.agent.capabilities.tools.execution.model;

import com.example.agent.capabilities.tools.execution.mapping.ToolFieldKeys;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行结果对象。
 *
 * <p>用途：统一承载执行结果核心字段，并通过 Mapper 在边界层完成 Map 转换。</p>
 */
public class ToolExecutionResult {

    /**
     * 工具名称。
     */
    private String toolName;
    /**
     * 调用输出载荷。
     */
    private ToolInvocationPayload payload;
    /**
     * 计量信息。
     */
    private Map<String, Object> tokenUsage;
    /**
     * 是否缓存命中。
     */
    private boolean cacheHit;
    /**
     * 原始输出引用。
     */
    private String rawRef;
    /**
     * 结果摘要。
     */
    private String resultDigest;

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public ToolInvocationPayload getPayload() {
        return payload;
    }

    public void setPayload(ToolInvocationPayload payload) {
        this.payload = payload;
    }

    public Map<String, Object> getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(Map<String, Object> tokenUsage) {
        this.tokenUsage = tokenUsage;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }

    public String getResultDigest() {
        return resultDigest;
    }

    public void setResultDigest(String resultDigest) {
        this.resultDigest = resultDigest;
    }

    /**
     * 输出 Map 形式结果。
     *
     * @return 结果映射
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put(ToolFieldKeys.TOOL, toolName);
        result.put(ToolFieldKeys.RESULT, payload == null ? Map.of() : payload.getValues());
        result.put(ToolFieldKeys.TOKEN_USAGE, tokenUsage == null ? Map.of() : tokenUsage);
        result.put(ToolFieldKeys.CACHE_HIT, cacheHit);
        result.put(ToolFieldKeys.RAW_REF, rawRef);
        result.put(ToolFieldKeys.RESULT_DIGEST, resultDigest);
        return result;
    }
}
