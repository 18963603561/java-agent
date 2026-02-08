package com.example.agent.runtime.llm;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.provider.ModelRequest;
import com.example.agent.capabilities.llm.provider.ModelResponse;
import com.example.agent.capabilities.llm.provider.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 工具结果摘要服务。
 *
 * <p>用途：将工具调用结果转换为可面向用户的摘要输出，支持模型总结与模板兜底。
 * <p>输入：查询文本、工具调用结果、工具元信息与链路上下文。
 * <p>输出：摘要输出与摘要原始引用。
 * <p>边界：模型摘要解析失败时回退为纯文本摘要，避免流程中断。
 */
@Service
public class ToolSummaryService {

    private final ModelInvocationService modelInvocationService;
    private final PromptAssembler promptAssembler;
    private final LlmDecisionService llmDecisionService;

    public ToolSummaryService(ModelInvocationService modelInvocationService,
                              PromptAssembler promptAssembler,
                              LlmDecisionService llmDecisionService) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.llmDecisionService = llmDecisionService;
    }

    /**
     * 总结工具结果。
     *
     * @param query 用户问题
     * @param decision 决策输出
     * @param toolResult 工具结果
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 序列计数器
     * @return 摘要输出
     */
    public ToolSummaryOutput summarizeToolResult(String query,
                                                 Map<String, Object> decision,
                                                 ToolCallOrchestrator.ToolCallOutcome toolResult,
                                                 String toolName,
                                                 Map<String, Object> toolArguments,
                                                 TaskRequest request,
                                                 Map<String, Object> stepInput,
                                                 TenantContext tenantContext,
                                                 String workflowId,
                                                 AtomicLong seqCounter) {
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put("query", query);
        toolContext.put("tool", toolName);
        toolContext.put("arguments", toolArguments == null ? Map.of() : toolArguments);
        toolContext.put("status", toolResult != null ? toolResult.getStatus() : null);
        toolContext.put("result", toolResult != null && toolResult.getResult() != null ? toolResult.getResult() : Map.of());
        if (toolResult != null && toolResult.getErrorCode() != null) {
            toolContext.put("errorCode", toolResult.getErrorCode());
        }
        if (toolResult != null && toolResult.getErrorMessage() != null) {
            toolContext.put("errorMessage", toolResult.getErrorMessage());
        }
        if (decision != null) {
            toolContext.put("decision", decision);
        }

        String summaryPrompt = buildSummaryPrompt(llmDecisionService.toJson(toolContext));
        ModelRequest summaryRequest = new ModelRequest(summaryPrompt, ModelScene.LLM_STEP);
        applyPromptBundle(summaryRequest, summaryPrompt, request, stepInput);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("promptScene", "llm_step_summary");
        metadata.put("ts", Instant.now().toString());
        ModelResponse summaryResponse = modelInvocationService.invoke(
                summaryRequest,
                ModelScene.LLM_STEP,
                tenantContext,
                workflowId,
                seqCounter,
                "llm_step_summary",
                metadata
        );

        String rawSummary = summaryResponse != null ? summaryResponse.getContent() : null;
        String summaryRawRef = summaryResponse != null ? summaryResponse.getRawRef() : null;
        Map<String, Object> parsed = llmDecisionService.parseJsonMap(rawSummary);
        if (parsed.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("answer", StringUtils.hasText(rawSummary) ? rawSummary : "no_response");
            boolean success = toolResult != null
                    && ToolCallOrchestrator.TOOL_STATUS_SUCCESS.equals(toolResult.getStatus());
            fallback.put("highlights", success ? "工具执行成功" : "工具执行失败");
            fallback.put("confidence", success ? 0.5 : 0.2);
            return new ToolSummaryOutput(fallback, summaryRawRef);
        }
        return new ToolSummaryOutput(parsed, summaryRawRef);
    }

    /**
     * 生成摘要提示词。
     *
     * @param contextJson 摘要上下文
     * @return 提示词
     */
    public String buildSummaryPrompt(String contextJson) {
        return """
                你是任务执行助手（LLM Step Runner - Tool Result Summarizer）。
                你的任务：仅依据 LLM_STEP_TOOL_RESULT_JSON 中的工具执行结果，生成最终回答（answer）。

                【核心原则】
                - 禁止编造：不得生成工具结果中不存在的事实、列表或数值。
                - 只做汇总：优先提炼关键字段与结论，避免复制大段原文。
                - answer 面向用户；highlights 面向审计/证据。

                【成功/失败处理规则】
                1) 工具执行成功（status=COMPLETED/OK 且无 error）：
                   - 若结果有数据：answer 给出结论与必要的结果摘要；highlights 提取关键证据（数量/关键字段/示例）。
                   - 若结果为空（如 total=0/rows=[]/no match）：answer 明确说明“未找到/结果为空”，并给出可调整的查询建议（例如扩大条件、检查关键词）；highlights 说明“空结果证据”。

                2) 工具执行失败（status=FAILED 或存在 error）：
                   - answer 必须说明失败原因（从 error.message/errorCode 提取，避免长堆栈），并给出可执行下一步建议：
                     * 需要补充哪些参数/权限
                     * 是否改用其他工具/更换查询条件
                     * 是否重试（并说明建议的修改）
                   - highlights 必须是失败原因摘要（errorCode + 简短原因）。

                【长度与合规】
                - answer：尽量简洁，避免粘贴原始返回；如有列表，只展示前 N 条（N<=5）。
                - highlights：1~2 句，<= 200 字符；禁止长引用/堆栈复制。

                【置信度（confidence）规则】
                - 有明确成功结果且证据充分：0.7~0.95
                - 成功但结果为空：0.5~0.7
                - 失败但原因明确且建议可执行：0.3~0.5
                - 失败且原因不明/上下文不足：0.0~0.3

                【输出格式】
                只能输出一个 JSON 对象，不能包含任何其他文本，不能使用 Markdown/代码块。

                输出 JSON 规范：
                {
                  "answer": "...",
                  "highlights": "关键证据或失败原因摘要",
                  "confidence": 0.0 ~ 1.0
                }

                输入上下文（JSON）：
                LLM_STEP_TOOL_RESULT_JSON:%s
                """.formatted(contextJson == null ? "{}" : contextJson);
    }

    /**
     * 生成模板答案。
     *
     * @param success 是否成功
     * @param toolResult 工具结果
     * @return 模板答案
     */
    public String buildTemplateAnswer(boolean success, Map<String, Object> toolResult) {
        if (!success) {
            return "工具执行失败，请查看 rawResult";
        }
        if (toolResult == null || toolResult.isEmpty()) {
            return "工具结果为空";
        }
        List<String> keys = new ArrayList<>(toolResult.keySet());
        int limit = Math.min(keys.size(), 5);
        String joined = String.join(",", keys.subList(0, limit));
        return "工具执行完成，返回字段：" + joined;
    }

    /**
     * 生成默认答案。
     *
     * @param success 是否成功
     * @param toolResult 工具结果
     * @return 默认答案
     */
    public String buildDefaultAnswer(boolean success, Map<String, Object> toolResult) {
        if (!success) {
            return "工具执行失败，请查看 rawResult";
        }
        if (toolResult == null || toolResult.isEmpty()) {
            return "工具结果为空";
        }
        return "已返回工具结果，请查看 rawResult";
    }

    /**
     * 生成模板 highlights。
     *
     * @param toolResult 工具结果
     * @return 文本
     */
    public String buildTemplateHighlights(Map<String, Object> toolResult) {
        if (toolResult == null || toolResult.isEmpty()) {
            return "结果为空";
        }
        List<String> keys = new ArrayList<>(toolResult.keySet());
        int limit = Math.min(keys.size(), 5);
        String joined = String.join(",", keys.subList(0, limit));
        return "返回字段：" + joined;
    }

    /**
     * 生成默认 highlights。
     *
     * @param success 是否成功
     * @param errorCode 错误码
     * @return 文本
     */
    public String buildDefaultHighlights(boolean success, String errorCode) {
        if (success) {
            return "工具原始结果";
        }
        if (StringUtils.hasText(errorCode)) {
            return "工具执行失败：" + errorCode;
        }
        return "工具执行失败";
    }

    private void applyPromptBundle(ModelRequest request,
                                   String prompt,
                                   TaskRequest taskRequest,
                                   Map<String, Object> stepInput) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, taskRequest, stepInput);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }

    /**
     * 工具摘要输出。
     */
    public static final class ToolSummaryOutput {

        private final Map<String, Object> output;
        private final String summaryRawRef;

        public ToolSummaryOutput(Map<String, Object> output, String summaryRawRef) {
            this.output = output == null ? new HashMap<>() : output;
            this.summaryRawRef = summaryRawRef;
        }

        public Map<String, Object> getOutput() {
            return output;
        }

        public String getSummaryRawRef() {
            return summaryRawRef;
        }
    }
}
