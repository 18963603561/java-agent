package com.example.agent.orchestration.multiagent.role;

import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 角色规划修复服务。
 * <p>用途：封装 JSON 修复调用细节，避免门面类直接依赖修复协议。</p>
 */
@Component
public class RolePlanRepairService {

    private static final Logger log = LoggerFactory.getLogger(RolePlanRepairService.class);

    private final ObjectMapper objectMapper;
    private final JsonOutputRepairService jsonOutputRepairService;

    public RolePlanRepairService(ObjectMapper objectMapper,
                                 JsonOutputRepairService jsonOutputRepairService) {
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 尝试修复角色规划输出。
     *
     * @param rawContent 原始输出
     * @param inputSummary 输入摘要
     * @param context 上下文
     * @return 修复后的文本，失败返回 null
     */
    public String repair(String rawContent,
                         Map<String, Object> inputSummary,
                         RoleResolveContext context) {
        // 关键逻辑：修复服务缺失或原文为空时直接返回，避免无效调用。
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return null;
        }
        Map<String, Object> contextMap = inputSummary != null ? new HashMap<>(inputSummary) : new HashMap<>();
        String contextJson = serializeContext(contextMap);
        // 关键逻辑：按 multiagent schema 执行一次修复，避免无限修复循环。
        String repaired = jsonOutputRepairService.repair("multiagent",
                rawContent,
                JsonOutputSchema.MULTIAGENT,
                contextJson,
                1);
        if (!StringUtils.hasText(repaired)) {
            log.warn("多智能体输出修复失败, workflowId={}, stepType={}",
                    context.workflowId(),
                    context.stepType());
            return null;
        }
        return repaired;
    }

    private String serializeContext(Map<String, Object> contextMap) {
        try {
            return objectMapper.writeValueAsString(contextMap);
        } catch (Exception ex) {
            log.warn("多智能体修复上下文序列化失败，使用空上下文", ex);
            return "{}";
        }
    }
}

