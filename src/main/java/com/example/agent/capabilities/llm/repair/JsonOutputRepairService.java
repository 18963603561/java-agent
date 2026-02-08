package com.example.agent.capabilities.llm.repair;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.provider.ModelRequest;
import com.example.agent.capabilities.llm.provider.ModelResponse;
import com.example.agent.capabilities.llm.provider.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * JSON 输出修复服务。
 *
 * <p>用途：当模型输出不满足预期结构时，调用低成本模型进行格式修复。
 * <p>输入：修复请求对象。
 * <p>输出：修复后的 JSON 文本；失败返回 null。
 * <p>边界：关键输入缺失时直接返回 null，不触发模型调用。
 */
@Service
public class JsonOutputRepairService {

    private static final Logger log = LoggerFactory.getLogger(JsonOutputRepairService.class);

    private final ModelInvocationService modelInvocationService;
    private final PromptAssembler promptAssembler;
    private final MetricsPublisher metricsPublisher;
    private final ValidationSupport validationSupport;

    @Autowired
    public JsonOutputRepairService(ModelInvocationService modelInvocationService,
                                   PromptAssembler promptAssembler,
                                   MetricsPublisher metricsPublisher,
                                   ValidationSupport validationSupport) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.metricsPublisher = metricsPublisher;
        this.validationSupport = validationSupport;
    }

    /**
     * 构造 JSON 修复服务（测试简化入口）。
     *
     * @param modelInvocationService 模型调用服务
     * @param promptAssembler 提示词组装器
     * @param metricsPublisher 指标发布器
     */
    public JsonOutputRepairService(ModelInvocationService modelInvocationService,
                                   PromptAssembler promptAssembler,
                                   MetricsPublisher metricsPublisher) {
        this(modelInvocationService, promptAssembler, metricsPublisher, new ValidationSupport());
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
        JsonRepairRequest request = new JsonRepairRequest(sceneId, rawModelText, schema, contextJson, maxAttempts);
        return repair(request);
    }

    /**
     * 尝试修复 JSON 输出。
     *
     * @param request 修复请求
     * @return 修复后的 JSON 文本或 null
     */
    public String repair(JsonRepairRequest request) {
        if (!isRepairable(request)) {
            log.warn("JSON 修复跳过, sceneId={}, reason=invalid_request",
                    request != null ? request.getSceneId() : null);
            return null;
        }
        String sceneId = validationSupport.normalizeText(request.getSceneId(), "unknown");
        int attempts = validationSupport.normalizeMaxAttempts(request.getMaxAttempts(), 1, sceneId);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (metricsPublisher != null) {
                metricsPublisher.incrementWithTags("json_repair_attempt_total", "scene", sceneId);
            }
            String prompt = buildPrompt(request.getSchema(), request.getRawModelText(), request.getContextJson());
            ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.CHEAP);
            applyPromptBundle(modelRequest, prompt);
            long start = System.currentTimeMillis();
            JsonRepairMetadata metadata = JsonRepairMetadata.of(sceneId);
            ModelResponse response = modelInvocationService.invoke(modelRequest,
                    ModelScene.CHEAP,
                    null,
                    null,
                    null,
                    "json_repair",
                    metadata.toMetadataMap());
            if (metricsPublisher != null) {
                metricsPublisher.recordTime("json_repair_latency_ms", System.currentTimeMillis() - start, sceneId);
            }
            if (response != null && StringUtils.hasText(response.getContent())) {
                if (metricsPublisher != null) {
                    metricsPublisher.incrementWithTags("json_repair_success_total", "scene", sceneId);
                }
                return response.getContent().trim();
            }
            log.warn("JSON 修复无响应, scene={}, attempt={}", sceneId, attempt);
        }
        if (metricsPublisher != null) {
            metricsPublisher.incrementWithTags("json_repair_failure_total", "scene", sceneId);
        }
        return null;
    }

    private boolean isRepairable(JsonRepairRequest request) {
        if (request == null) {
            return false;
        }
        if (!StringUtils.hasText(request.getRawModelText())) {
            return false;
        }
        if (request.getSchema() == null) {
            return false;
        }
        return modelInvocationService != null;
    }

    private String buildPrompt(JsonOutputSchema schema, String rawModelText, String contextJson) {
        StringBuilder builder = new StringBuilder();
        builder.append("只输出合法 JSON 对象，不要解释，不要 Markdown，不要多余字符。\n");
        builder.append("必须字段与类型\n");
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
}
