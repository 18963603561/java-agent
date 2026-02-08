package com.example.agent.capabilities.llm.repair;

/**
 * JSON 修复请求对象。
 *
 * <p>用途：承载 JSON 修复链路所需输入，替代散落参数传递。
 * <p>输入：场景、原始模型输出、输出结构约束、上下文与重试次数。
 * <p>输出：供修复服务内部构造提示词与调用模型。
 * <p>边界：对象创建不做业务校验，校验由修复服务统一执行。
 */
public class JsonRepairRequest {

    private final String sceneId;
    private final String rawModelText;
    private final JsonOutputSchema schema;
    private final String contextJson;
    private final int maxAttempts;

    public JsonRepairRequest(String sceneId,
                             String rawModelText,
                             JsonOutputSchema schema,
                             String contextJson,
                             int maxAttempts) {
        this.sceneId = sceneId;
        this.rawModelText = rawModelText;
        this.schema = schema;
        this.contextJson = contextJson;
        this.maxAttempts = maxAttempts;
    }

    public String getSceneId() {
        return sceneId;
    }

    public String getRawModelText() {
        return rawModelText;
    }

    public JsonOutputSchema getSchema() {
        return schema;
    }

    public String getContextJson() {
        return contextJson;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
