package com.example.agent.capabilities.context.compression.infrastructure.llm;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.application.LlmCompressionOrchestrator;
import com.example.agent.capabilities.context.compression.application.model.LlmCompressionCommand;
import com.example.agent.capabilities.context.compression.application.model.LlmCompressionResult;
import com.example.agent.capabilities.context.compression.application.port.CompressionExecutor;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.memory.model.ConversationSummary;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM 压缩执行器适配器。
 *
 * <p>用途：提供 P0 阶段 LLM 压缩占位实现，并通过统一失败语义触发上游降级策略。</p>
 */
@Component
public class LlmCompressionExecutorAdapter implements CompressionExecutor {

    /**
     * LLM 压缩模式标识。
     */
    public static final String MODE = "llm";

    private final LlmCompressionOrchestrator llmCompressionOrchestrator;

    public LlmCompressionExecutorAdapter(LlmCompressionOrchestrator llmCompressionOrchestrator) {
        this.llmCompressionOrchestrator = llmCompressionOrchestrator;
    }

    @Override
    public String mode() {
        return MODE;
    }

    @Override
    public CompressionExecutionResult execute(CompressionCommand command) {
        CompressionExecutionResult result = new CompressionExecutionResult();
        result.setSource(MODE);
        if (command == null || command.getRequest() == null || command.getRequest().getSnapshot() == null) {
            result.setSuccess(false);
            result.setFailureReason("INVALID_REQUEST");
            return result;
        }
        if (llmCompressionOrchestrator == null) {
            result.setSuccess(false);
            result.setFailureReason("DEPENDENCY_UNAVAILABLE");
            return result;
        }

        // 命令映射：将统一压缩命令转换为 LLM 编排命令。
        LlmCompressionCommand llmCommand = new LlmCompressionCommand();
        llmCommand.setSnapshot(command.getRequest().getSnapshot());
        llmCommand.setTenantContext(command.getTenantContext());
        llmCommand.setWorkflowId(command.getRequest().getWorkflowId());
        llmCommand.setSessionId(command.getRequest().getSessionId());
        llmCommand.setTriggerReason(command.getRequest().getTrimReport() != null ? "TRIM_REPORT" : "BUDGET_TRIGGER");
        llmCommand.setWindowShapeResult(command.getWindowShapeResult());

        // 执行编排：由 LLM 编排服务完成调用、解析与治理。
        LlmCompressionResult llmResult = llmCompressionOrchestrator.compress(llmCommand);
        result.setDurationMs(llmResult.getDurationMs());
        result.setOriginalTokens(llmResult.getInputTokens());
        result.setCompressedTokens(llmResult.getOutputTokens());
        result.setRetryCount(llmResult.getRetryCount());
        result.setFallbackApplied(llmResult.isFallbackApplied());
        result.setFailureReason(llmResult.getFailureReason());
        result.setSuccess(llmResult.isSuccess());

        // 成功映射：将摘要结果映射为统一 MemoryRecord，供上游回填使用。
        if (llmResult.isSuccess()) {
            MemoryRecord compressed = buildCompressedRecord(llmResult, llmCommand.getSessionId());
            result.setCompressed(compressed);
        }
        return result;
    }

    /**
     * 构造压缩记忆记录。
     */
    private MemoryRecord buildCompressedRecord(LlmCompressionResult llmResult, String sessionId) {
        MemoryRecord record = new MemoryRecord();
        record.setMemoryId("llm-" + java.util.UUID.randomUUID());
        record.setSessionId(sessionId);
        record.setSummary(llmResult.getSummary());

        ConversationSummary conversationSummary = new ConversationSummary();
        conversationSummary.setVersion(StringUtils.hasText(llmResult.getSummaryVersion())
                ? llmResult.getSummaryVersion()
                : "v1");
        conversationSummary.setSummary(llmResult.getSummary());
        conversationSummary.setSummaryChars(llmResult.getSummary() != null ? llmResult.getSummary().length() : 0);
        record.setConversationSummary(conversationSummary);
        return record;
    }
}

