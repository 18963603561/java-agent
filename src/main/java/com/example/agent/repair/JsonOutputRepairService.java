package com.example.agent.repair;

import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.observability.MetricsPublisher;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * JSON 输出修复服务。
 */
@Service
public class JsonOutputRepairService {

    private static final Logger log = LoggerFactory.getLogger(JsonOutputRepairService.class);

    private final ModelInvocationService modelInvocationService;
    private final PromptAssembler promptAssembler;
    private final MetricsPublisher metricsPublisher;

    public JsonOutputRepairService(ModelInvocationService modelInvocationService,
                                   PromptAssembler promptAssembler,
                                   MetricsPublisher metricsPublisher) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 尝试修复 JSON 输出。
     *
     * @param sceneId 场景标识
     * @param rawModelText 原始模型输出
     * @param schema 期望结构
     * @param contextJson 上下文 JSON
     * @param maxAttempts 最大尝试次数
     * @return 修复后的 JSON 文本或 null
     */
    public String repair(String sceneId,
                         String rawModelText,
                         JsonOutputSchema schema,
                         String contextJson,
                         int maxAttempts) {
        if (!StringUtils.hasText(rawModelText) || schema == null || modelInvocationService == null) {
            return null;
        }
        int attempts = Math.max(1, maxAttempts);
        for (int i = 1; i <= attempts; i++) {
            metricsPublisher.incrementWithTags("json_repair_attempt_total", "scene", safeTag(sceneId));
            String prompt = buildPrompt(schema, rawModelText, contextJson);
            ModelRequest request = new ModelRequest(prompt, ModelScene.CHEAP);
            applyPromptBundle(request, prompt);
            long start = System.currentTimeMillis();
            java.util.Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("scene", safeTag(sceneId));
            metadata.put("ts", Instant.now().toString());
            metadata.put("promptScene", safeTag(sceneId));
            ModelResponse response = modelInvocationService.invoke(request,
                    ModelScene.CHEAP,
                    null,
                    null,
                    null,
                    "json_repair",
                    metadata);
            metricsPublisher.recordTime("json_repair_latency_ms", System.currentTimeMillis() - start,
                    safeTag(sceneId));
            if (response != null && StringUtils.hasText(response.getContent())) {
                metricsPublisher.incrementWithTags("json_repair_success_total", "scene", safeTag(sceneId));
                return response.getContent().trim();
            }
            log.warn("JSON 修复无响应, scene={}, attempt={}", sceneId, i);
        }
        metricsPublisher.incrementWithTags("json_repair_failure_total", "scene", safeTag(sceneId));
        return null;
    }

    private String buildPrompt(JsonOutputSchema schema, String rawModelText, String contextJson) {
        StringBuilder builder = new StringBuilder();
        builder.append("只输出合法 JSON 对象，不要解释，不要 Markdown，不要多余字符。\n");
        builder.append("必须字段与类型:\n");
        builder.append(schema.constraintText()).append("\n");
        builder.append("INPUT:\n");
        builder.append(rawModelText == null ? "" : rawModelText.trim()).append("\n");
        if (StringUtils.hasText(contextJson)) {
            builder.append("CONTEXT:\n");
            builder.append(contextJson.trim()).append("\n");
        }
        return builder.toString();
    }

    private void applyPromptBundle(ModelRequest request, String prompt) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, null, null);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }

    private String safeTag(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim();
    }
}
