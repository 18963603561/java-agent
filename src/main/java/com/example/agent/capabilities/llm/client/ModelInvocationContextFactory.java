package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.security.auth.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 模型调用上下文工厂。
 *
 * <p>用途：统一处理调用入参归一化、路由预解析与元数据补全，降低调用服务复杂度。
 */
@Component
public class ModelInvocationContextFactory {

    private final ModelRouter modelRouter;
    private final ValidationSupport validationSupport;

    public ModelInvocationContextFactory(ModelRouter modelRouter,
                                         ValidationSupport validationSupport) {
        this.modelRouter = modelRouter;
        this.validationSupport = validationSupport;
    }

    /**
     * 创建调用上下文。
     *
     * @param request 模型请求
     * @param scene 场景
     * @param phase 阶段
     * @param tenantContext 租户上下文
     * @param metadata 元数据
     * @return 调用上下文
     */
    public ModelInvocationContext create(ModelRequest request,
                                         ModelScene scene,
                                         String phase,
                                         TenantContext tenantContext,
                                         Map<String, Object> metadata) {
        ModelScene resolvedScene = scene != null ? scene : ModelScene.CHEAP;
        String resolvedPhase = validationSupport.normalizeText(phase, "unknown");
        ModelRequest safeRequest = request != null ? request : new ModelRequest();
        safeRequest.setScene(resolvedScene);
        ModelDefinition definition = modelRouter.route(resolvedScene);
        String traceId = tenantContext != null ? tenantContext.getTraceId() : null;
        Map<String, Object> runtimeMetadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        runtimeMetadata.putIfAbsent("scene", resolvedScene.name());
        String provider = definition != null ? definition.getProvider() : null;
        if (provider != null && !provider.isBlank()) {
            runtimeMetadata.putIfAbsent("provider", provider);
        }
        String promptScene = resolvePromptScene(resolvedPhase, runtimeMetadata);
        PromptTrace trace = PromptTrace.fromPrompt(promptScene, safeRequest.getPrompt());
        runtimeMetadata.put("promptTrace", trace);
        return new ModelInvocationContext(
                safeRequest,
                resolvedScene,
                resolvedPhase,
                definition,
                traceId,
                runtimeMetadata,
                trace,
                System.nanoTime());
    }

    private String resolvePromptScene(String phase, Map<String, Object> metadata) {
        if (metadata != null) {
            Object value = metadata.get("promptScene");
            if (value instanceof String scene && !scene.isBlank()) {
                return scene.trim();
            }
        }
        if (phase == null) {
            return "unknown";
        }
        return switch (phase) {
            case "plan" -> "planner";
            case "reflect" -> "reflect";
            case "finalize" -> "final";
            case "react_think" -> "react";
            case "cot" -> "cot";
            case "research" -> "research";
            case "debate" -> "debate";
            case "multi_agent" -> "multiagent";
            case "json_repair" -> "repair";
            default -> phase;
        };
    }
}

