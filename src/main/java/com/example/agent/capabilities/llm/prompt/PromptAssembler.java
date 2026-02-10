package com.example.agent.capabilities.llm.prompt;

import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import java.util.Map;

/**
 * 提示词组装器接口。
 */
public interface PromptAssembler {

    /**
     * 组装提示词消息。
     *
     * @param prompt 用户提示内容
     * @param taskContext LLM 任务上下文
     * @param stepInput 步骤输入
     * @return 组装结果
     */
    PromptBundle build(String prompt, LlmTaskContext taskContext, Map<String, Object> stepInput);
}
