package com.example.agent.planning.approval;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.governance.evaluation.CapabilityEvaluationResult;
import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.runtime.model.StepSpec;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规划审批策略服务。
 *
 * <p>用途：统一根据评估结果补充审批要求，避免审批规则散落在编排流程中。
 */
@Component
public class PlanningApprovalService {

    private static final Logger log = LoggerFactory.getLogger(PlanningApprovalService.class);

    /**
     * 对规划结果应用审批要求。
     *
     * @param plan 规划结果
     * @param request 任务请求
     * @param evaluation 能力评估结果
     */
    public void applyApprovalRequirement(PlanResult plan,
                                         TaskRequest request,
                                         CapabilityEvaluationResult evaluation) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return;
        }
        if (evaluation == null || evaluation.isSkipped() || !evaluation.isShouldAskApproval()) {
            return;
        }
        if (hasExplicitApproval(request) || hasExplicitApproval(plan.getSteps())) {
            return;
        }
        StepSpec first = plan.getSteps().get(0);
        markStepRequiresApproval(first, "evaluation");
        log.info("规划步骤补充审批标记, stepType={}, source=evaluation",
                first != null ? first.getStepType() : null);
    }

    private boolean hasExplicitApproval(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return false;
        }
        return request.getContext().containsKey(PlanningContextKeys.REQUIRES_APPROVAL);
    }

    private boolean hasExplicitApproval(List<StepSpec> steps) {
        if (steps == null) {
            return false;
        }
        for (StepSpec step : steps) {
            if (step != null && step.getRequiresApproval() != null) {
                return true;
            }
        }
        return false;
    }

    private void markStepRequiresApproval(StepSpec step, String source) {
        if (step == null) {
            return;
        }
        step.setRequiresApproval(true);
        if (source != null && !source.isBlank()) {
            step.setApprovalSource(source);
        }
    }
}

