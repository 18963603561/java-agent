package com.example.agent.capabilities.memory.write;

import com.example.agent.security.redaction.RedactionResult;
import com.example.agent.security.redaction.RedactionService;
import com.example.agent.security.redaction.RedactionStage;
import org.springframework.stereotype.Component;

/**
 * 记忆写入脱敏处理器，负责统一封装写入前脱敏逻辑。
 */
@Component
public class MemoryWriteRedactionProcessor {

    private final RedactionService redactionService;

    public MemoryWriteRedactionProcessor(RedactionService redactionService) {
        this.redactionService = redactionService;
    }

    /**
     * 执行写入阶段脱敏。
     *
     * @param text 原始文本
     * @param fieldKey 字段标识
     * @return 脱敏结果
     */
    public RedactionResult redactForWrite(String text, String fieldKey) {
        if (redactionService == null) {
            RedactionResult result = new RedactionResult();
            result.setRedactedText(text);
            return result;
        }
        return redactionService.apply(text, RedactionStage.WRITE, fieldKey);
    }

    public boolean isEnabled() {
        return redactionService != null && redactionService.isEnabled();
    }

    public boolean isRejectOnSecrets() {
        return redactionService != null && redactionService.isRejectOnSecrets();
    }

    public boolean isRedactOnPii() {
        return redactionService != null && redactionService.isRedactOnPii();
    }
}

