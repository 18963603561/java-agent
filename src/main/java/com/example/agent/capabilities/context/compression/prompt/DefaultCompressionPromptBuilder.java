package com.example.agent.capabilities.context.compression.prompt;

import com.example.agent.capabilities.context.compression.application.model.LlmCompressionCommand;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.WorkingMemory;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认压缩提示词构建器。
 */
@Component
public class DefaultCompressionPromptBuilder implements CompressionPromptBuilder {

    private static final int MAX_WORKING_SUMMARY_CHARS = 2000;
    private static final int MAX_MEMORY_REF_COUNT = 8;
    private static final int MAX_MEMORY_REF_CHARS = 300;

    @Override
    public String buildPrompt(LlmCompressionCommand command) {
        StringBuilder builder = new StringBuilder();
        // 模板注入：写入压缩指令与输出约束。
        builder.append(CompressionPromptTemplate.template()).append('\n');
        if (command == null || command.getSnapshot() == null) {
            return builder.toString();
        }

        // 快照抽取：取工作记忆与长期记忆引用作为压缩输入。
        ContextSnapshot snapshot = command.getSnapshot();
        WorkingMemory workingMemory = snapshot.getWorkingMemory();
        LongTermMemory longTermMemory = snapshot.getLongTermMemory();

        builder.append("INPUT:\n");
        // 任务上下文写入：用于引导模型理解压缩触发背景。
        builder.append("workflowId=").append(command.getWorkflowId()).append('\n');
        builder.append("sessionId=").append(command.getSessionId()).append('\n');
        builder.append("triggerReason=").append(command.getTriggerReason()).append('\n');

        // 工作记忆写入：优先使用 summary，按上限裁剪避免提示词过长。
        String workingSummary = workingMemory != null ? workingMemory.getSummary() : null;
        builder.append("workingSummary=")
                .append(trimText(workingSummary, MAX_WORKING_SUMMARY_CHARS))
                .append('\n');

        // 长期记忆写入：写入有限条引用片段，保障输入稳定与成本可控。
        builder.append("memoryRefs=\n");
        List<MemoryRef> refs = longTermMemory != null ? longTermMemory.getMemoryRefs() : null;
        if (refs != null && !refs.isEmpty()) {
            int written = 0;
            // 循环写入：按顺序写入引用片段，达到上限后停止。
            for (MemoryRef ref : refs) {
                // 空值过滤：忽略空引用，避免生成无效输入噪声。
                if (ref == null) {
                    continue;
                }
                // 条数限制：达到最大条数后立即退出循环。
                if (written >= MAX_MEMORY_REF_COUNT) {
                    break;
                }
                String snippet = trimText(ref.getSnippet(), MAX_MEMORY_REF_CHARS);
                builder.append("- ")
                        .append(ref.getMemoryId())
                        .append(":")
                        .append(snippet)
                        .append('\n');
                written++;
            }
        }
        return builder.toString();
    }

    /**
     * 裁剪文本。
     */
    private String trimText(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }
}

