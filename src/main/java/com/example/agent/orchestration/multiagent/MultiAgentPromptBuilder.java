package com.example.agent.orchestration.multiagent;

import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 多智能体提示词构建器。
 * <p>用途：统一提示词模板生成与消息包填充，避免协调器混入模板细节。
 */
@Component
public class MultiAgentPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentPromptBuilder.class);

    private final ObjectMapper objectMapper;
    private final PromptAssembler promptAssembler;

    public MultiAgentPromptBuilder(ObjectMapper objectMapper,
                                   PromptAssembler promptAssembler) {
        this.objectMapper = objectMapper;
        this.promptAssembler = promptAssembler;
    }

    /**
     * 构建提示词文本。
     */
    public String buildPrompt(Map<String, Object> inputSummary) {
        Map<String, Object> context = inputSummary != null
                ? new HashMap<>(inputSummary)
                : new HashMap<>();
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            log.warn("多智能体上下文序列化失败，使用空上下文", ex);
            contextJson = "{}";
        }
        return """
            你是多智能体团队协调器（team coordinator）。
            你的任务是根据 MULTI_AGENT_CONTEXT_JSON 中的 query、goal、constraints、tools，
            设计最小且必要的团队角色集合，并给出每个角色的职责说明。

            【团队设计规则】
            1) 角色数量应最小化，通常 1~4 个。
            2) 每个角色职责必须互补，禁止重复描述。
            3) 每个角色必须直接服务于当前任务目标。
            4) 简单问题只允许 1 个角色。

            【输出约束】
            1) 输出必须是单个 JSON 对象，禁止任何额外文本。
            2) 必须包含字段 team，且 team 为数组。
            3) team[*] 字段规范如下：
               - roleId: string，角色唯一标识
               - name: string，角色名称
               - modelId: string，可空
               - description: string，角色职责

            【最小输出示例】
            {"team":[{"roleId":"planner","name":"Planner","modelId":null,"description":"负责任务分解与计划制定"}]}

            MULTI_AGENT_CONTEXT_JSON:%s
            """.formatted(contextJson);
    }

    /**
     * 将提示词文本附加为消息包。
     */
    public void applyPromptBundle(ModelRequest request, String prompt, Map<String, Object> inputSummary) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, LlmTaskContext.empty(), inputSummary);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }
}
