package com.example.agent.reflection;

import com.example.agent.auth.TenantContext;
import com.example.agent.runtime.StepRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 反思服务，负责评估输出质量并给出重试建议。
 */
@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    /**
     * 进行反思评估。
     *
     * @param step 步骤请求
     * @param output 输出结果
     * @param tenantContext 租户上下文
     * @return 反思结果
     */
    public ReflectionResult reflect(StepRequest step, Map<String, Object> output, TenantContext tenantContext) {
        boolean retry = output != null && Boolean.TRUE.equals(output.get("retry"));
        ReflectionReport report = new ReflectionReport(retry ? 0.4 : 0.9,
                retry ? "输出需要重试" : "输出正常");
        log.info("反思完成, tenantId={}, stepType={}, retry={}",
                tenantContext.getTenantId(), step.getStepType(), retry);
        return new ReflectionResult(retry, report);
    }
}
