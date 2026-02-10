package com.example.agent.reflection.strategy;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.prompt.ReflectionPromptProvider;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 反思 LLM 调用执行器。
 * <p>用途：统一负责提示词构建、工具注入与模型调用，降低策略编排类的职责复杂度。</p>
 */
@Component
public class ReflectionLlmInvocationExecutor {

    /**
     * 模型调用服务。
     */
    private final ModelInvocationService modelInvocationService;

    /**
     * 工具注入解析器。
     */
    private final ModelToolResolver modelToolResolver;

    /**
     * 提示词装配器。
     */
    private final PromptAssembler promptAssembler;

    /**
     * 反思提示词提供器。
     */
    private final ReflectionPromptProvider reflectionPromptProvider;

    public ReflectionLlmInvocationExecutor(ModelInvocationService modelInvocationService,
                                           ModelToolResolver modelToolResolver,
                                           PromptAssembler promptAssembler,
                                           ReflectionPromptProvider reflectionPromptProvider) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.reflectionPromptProvider = reflectionPromptProvider;
    }

    /**
     * 执行一次反思模型调用。
     *
     * @param context 反思执行上下文
     * @return 调用结果对象
     */
    public ReflectionLlmInvocation invoke(ReflectionExecutionContext context) {
        String prompt = reflectionPromptProvider.buildPrompt(context != null ? context.getReflectionContext() : null);
        ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.REFLECT);
        applyPromptBundle(modelRequest, prompt, context);
        modelToolResolver.applyTooling(modelRequest, LlmTaskContext.empty(),
                context != null && context.getStep() != null ? context.getStep().toExecutionInput() : null);

        Map<String, Object> metadata = buildInvocationMetadata(context);
        ModelResponse response = modelInvocationService.invoke(
                modelRequest,
                ModelScene.REFLECT,
                context != null ? context.getTenantContext() : null,
                context != null ? context.getWorkflowId() : null,
                context != null ? context.getSeqCounter() : null,
                "reflect",
                metadata
        );
        return new ReflectionLlmInvocation(prompt, metadata, response);
    }

    private Map<String, Object> buildInvocationMetadata(ReflectionExecutionContext context) {
        Map<String, Object> metadata = new HashMap<>();
        if (context != null && context.getStep() != null && context.getStep().getStepType() != null) {
            metadata.put("stepType", context.getStep().getStepType());
        }
        metadata.put("attempt", context != null ? context.getAttempt() : 0);
        metadata.put("promptScene", "reflect");
        return metadata;
    }

    private void applyPromptBundle(ModelRequest modelRequest,
                                   String prompt,
                                   ReflectionExecutionContext context) {
        if (promptAssembler == null || modelRequest == null) {
            return;
        }
        Map<String, Object> input = context != null && context.getStep() != null
                ? context.getStep().toExecutionInput()
                : null;
        PromptBundle bundle = promptAssembler.build(prompt, LlmTaskContext.empty(), input);
        if (bundle != null && bundle.getMessages() != null) {
            modelRequest.setMessages(bundle.getMessages());
        }
    }
}
