package com.example.agent.planning.context;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.planning.PlanningContextKeys;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规划上下文映射器。
 *
 * <p>用途：将请求上下文标准化为 {@link PlanningContext}，并处理基础默认值。
 */
@Component
public class PlanningContextMapper {

    private static final Logger log = LoggerFactory.getLogger(PlanningContextMapper.class);

    /**
     * 映射任务请求为规划上下文。
     *
     * @param request 任务请求
     * @return 规划上下文
     */
    public PlanningContext fromTaskRequest(TaskRequest request) {
        Map<String, Object> raw = request != null && request.getContext() != null
                ? new HashMap<>(request.getContext())
                : new HashMap<>();
        String query = request != null ? request.getQuery() : null;
        log.debug("开始映射规划上下文, hasRequest={}, hasContext={}, queryLength={}",
                request != null,
                request != null && request.getContext() != null,
                query != null ? query.length() : 0);
        if (request != null && request.getToolChoice() != null
                && !raw.containsKey(PlanningContextKeys.TOOL_CHOICE)) {
            raw.put(PlanningContextKeys.TOOL_CHOICE, request.getToolChoice());
        }
        normalizeToolChoice(raw);
        PlanningContext context = new PlanningContext(raw);
        log.debug("规划上下文映射完成, keys={}, toolChoiceMode={}, toolsCount={}",
                raw.size(),
                context.getToolChoiceMode(),
                context.getTools().size());
        return context;
    }

    private void normalizeToolChoice(Map<String, Object> raw) {
        if (raw == null) {
            return;
        }
        Object value = raw.get(PlanningContextKeys.TOOL_CHOICE);
        ModelToolChoice normalized = ModelToolChoice.fromRaw(value);
        if (normalized == null) {
            if (value != null) {
                log.warn("规划上下文 toolChoice 非法，已忽略, rawType={}", value.getClass().getName());
            }
            raw.remove(PlanningContextKeys.TOOL_CHOICE);
            return;
        }
        raw.put(PlanningContextKeys.TOOL_CHOICE, normalized);
    }
}
