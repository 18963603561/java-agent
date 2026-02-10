package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import java.util.Map;

/**
 * 模型调用上下文。
 *
 * <p>用途：统一承载模型调用前后的标准上下文，减少调用服务内部的临时变量散落。
 * <p>输入：由上下文工厂在调用开始阶段构造。
 * <p>输出：供执行器与观测发布器共享。
 */
public class ModelInvocationContext {

    private final ModelRequest request;
    private final ModelScene scene;
    private final String phase;
    private final ModelDefinition modelDefinition;
    private final String traceId;
    private final Map<String, Object> metadata;
    private final PromptTrace promptTrace;

    /**
     * 调用开始纳秒时间。
     */
    private final long startNs;

    public ModelInvocationContext(ModelRequest request,
                                  ModelScene scene,
                                  String phase,
                                  ModelDefinition modelDefinition,
                                  String traceId,
                                  Map<String, Object> metadata,
                                  PromptTrace promptTrace,
                                  long startNs) {
        this.request = request;
        this.scene = scene;
        this.phase = phase;
        this.modelDefinition = modelDefinition;
        this.traceId = traceId;
        this.metadata = metadata == null ? Map.of() : metadata;
        this.promptTrace = promptTrace;
        this.startNs = startNs;
    }

    public ModelRequest getRequest() {
        return request;
    }

    public ModelScene getScene() {
        return scene;
    }

    public String getPhase() {
        return phase;
    }

    public ModelDefinition getModelDefinition() {
        return modelDefinition;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public PromptTrace getPromptTrace() {
        return promptTrace;
    }

    public long getStartNs() {
        return startNs;
    }

    /**
     * 获取模型标识。
     *
     * @return 模型标识
     */
    public String getModelId() {
        return modelDefinition != null ? modelDefinition.getModelId() : null;
    }

    /**
     * 获取提供商标识。
     *
     * @return 提供商标识
     */
    public String getProvider() {
        return modelDefinition != null ? modelDefinition.getProvider() : null;
    }
}

