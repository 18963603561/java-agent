package com.example.agent.governance.replay.domain;

import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.history.eventlog.EventLogRepository;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.security.auth.TenantContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 回放项装配器。
 */
@Component
public class ReplayItemAssembler {

    private final EventLogRepository eventLogRepository;
    private final StepRuntimeService stepRuntimeService;

    public ReplayItemAssembler(EventLogRepository eventLogRepository,
                               StepRuntimeService stepRuntimeService) {
        this.eventLogRepository = eventLogRepository;
        this.stepRuntimeService = stepRuntimeService;
    }

    /**
     * 装配回放项并排序。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @return 回放项列表
     */
    public List<ReplayItem> assemble(TenantContext tenantContext, String workflowId) {
        List<ReplayItem> items = new ArrayList<>();
        List<EventLogRecord> events = eventLogRepository.findByWorkflow(tenantContext.getTenantId(), workflowId);
        for (EventLogRecord record : events) {
            items.add(ReplayItem.fromEvent(record));
        }
        List<StepRecord> steps = stepRuntimeService.getSteps(workflowId, tenantContext);
        for (StepRecord step : steps) {
            items.add(ReplayItem.fromStep(step));
        }
        items.sort((left, right) -> {
            long leftSeq = left.getOriginalSeq();
            long rightSeq = right.getOriginalSeq();
            if (leftSeq > 0 && rightSeq > 0 && leftSeq != rightSeq) {
                return Long.compare(leftSeq, rightSeq);
            }
            return left.getTimestamp().compareTo(right.getTimestamp());
        });
        return items;
    }
}

