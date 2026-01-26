package com.example.agent.planning;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.runtime.StepRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 规划服务，负责根据任务生成可执行步骤。
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    /**
     * 生成规划结果。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 规划结果
     */
    public PlanResult plan(TaskRequest request, TenantContext tenantContext) {
        String planId = UUID.randomUUID().toString();
        StepRequest step = new StepRequest("TOOL", Map.of(
                "query", request.getQuery(),
                "context", request.getContext() == null ? Map.of() : request.getContext()
        ));
        PlanResult result = new PlanResult(planId, "default-plan", List.of(step));
        log.info("规划生成, tenantId={}, planId={}, steps={}",
                tenantContext.getTenantId(), planId, result.getSteps().size());
        return result;
    }
}
