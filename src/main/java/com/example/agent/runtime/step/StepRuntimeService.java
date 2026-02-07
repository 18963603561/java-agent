package com.example.agent.runtime.step;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.runtime.step.repository.StepRecordRepository;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 步骤运行时服务，负责步骤记录落地与事件发布。
 */
@Service
public class StepRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(StepRuntimeService.class);

    private final StepStateMachine stateMachine = new StepStateMachine();
    private final MetricsPublisher metricsPublisher;
    private final StepRecordRepository stepRecordRepository;
    private final StepRuntimeEventService stepRuntimeEventService;
    private final StepSummaryRegenerationService stepSummaryRegenerationService;
    private final StepEventPayloadFactory stepEventPayloadFactory;
    private final StepResultAssembler stepResultAssembler;

    public StepRuntimeService(MetricsPublisher metricsPublisher,
                              StepRecordRepository stepRecordRepository,
                              StepRuntimeEventService stepRuntimeEventService,
                              StepSummaryRegenerationService stepSummaryRegenerationService,
                              StepEventPayloadFactory stepEventPayloadFactory,
                              StepResultAssembler stepResultAssembler) {
        this.metricsPublisher = metricsPublisher;
        this.stepRecordRepository = stepRecordRepository;
        this.stepRuntimeEventService = stepRuntimeEventService;
        this.stepSummaryRegenerationService = stepSummaryRegenerationService;
        this.stepEventPayloadFactory = stepEventPayloadFactory;
        this.stepResultAssembler = stepResultAssembler;
    }

    /**
     * 开始步骤并发布事件。
     *
     * @param workflowId 工作流标识
     * @param stepType 步骤类型
     * @param attempt 尝试次数
     * @param input 输入内容
     * @param tenantContext 租户上下文
     * @param seqCounter 序列号计数器
     * @return 步骤记录
     */
    public StepRecord startStep(String workflowId,
                                String stepType,
                                int attempt,
                                Map<String, Object> input,
                                TenantContext tenantContext,
                                AtomicLong seqCounter) {
        long seq = stepRuntimeEventService.nextSequence(tenantContext, workflowId, seqCounter);
        StepRecord record = new StepRecord();
        record.setStepId(UUID.randomUUID().toString());
        record.setWorkflowId(workflowId);
        record.setStepSeq(seq);
        record.setType(stepType);
        record.setStatus(stateMachine.transition(null, StepState.STARTED));
        record.setAttempt(attempt);
        record.setInput(input);
        record.setTenantId(tenantContext.getTenantId());
        record.setStartedAt(Instant.now());

        saveRecord(record);
        metricsPublisher.increment("step.count", stepRuntimeEventService.resolveTraceId(tenantContext));
        stepRuntimeEventService.publish(EventType.STEP_STARTED,
                record,
                seq,
                stepEventPayloadFactory.buildStepStartedPayload(record),
                tenantContext);
        log.info("步骤开始, tenantId={}, workflowId={}, stepId={}, seq={}",
                tenantContext.getTenantId(), workflowId, record.getStepId(), seq);
        return record;
    }

    /**
     * 完成步骤并发布事件。
     *
     * @param record 步骤记录
     * @param output 输出对象
     * @param seqCounter 序列号计数器
     * @return 更新后的步骤记录
     */
    public StepRecord completeStep(StepRecord record, StepExecutionOutput output, AtomicLong seqCounter) {
        record.setStatus(stateMachine.transition(record.getStatus(), StepState.COMPLETED));
        record.setCompletedAt(Instant.now());
        Map<String, Object> rawOutput = output != null ? output.getPayload() : null;
        Map<String, Object> summary = output != null ? output.getSummary() : Collections.emptyMap();
        summary = stepSummaryRegenerationService.resolveSummary(record, output, rawOutput, summary);
        StepResult stepResult = stepResultAssembler.assemble(record, output, summary);
        record.setOutput(stepResult);
        metricsPublisher.recordTime("step.duration.ms", calcDuration(record), stepRuntimeEventService.resolveTraceId(null));

        TenantContext eventTenantContext = new TenantContext(record.getTenantId(), null, Collections.emptyList(), null, null);
        long seq = stepRuntimeEventService.nextSequence(eventTenantContext,
                record.getWorkflowId(), seqCounter);
        stepRuntimeEventService.publish(EventType.STEP_COMPLETED,
                record,
                seq,
                stepEventPayloadFactory.buildStepCompletedPayload(record),
                eventTenantContext);
        saveRecord(record);
        log.info("步骤完成, tenantId={}, workflowId={}, stepId={}, seq={}",
                record.getTenantId(), record.getWorkflowId(), record.getStepId(), record.getStepSeq());
        return record;
    }

    /**
     * 步骤失败并发布事件。
     *
     * @param record 步骤记录
     * @param errorCode 错误码
     * @param details 失败详情
     * @param seqCounter 序列号计数器
     * @return 更新后的步骤记录
     */
    public StepRecord failStep(StepRecord record,
                               String errorCode,
                               Map<String, Object> details,
                               AtomicLong seqCounter) {
        record.setStatus(stateMachine.transition(record.getStatus(), StepState.FAILED));
        record.setErrorCode(errorCode);
        record.setCompletedAt(Instant.now());
        metricsPublisher.increment("step.failure.count", stepRuntimeEventService.resolveTraceId(null));

        TenantContext eventTenantContext = new TenantContext(record.getTenantId(), null, Collections.emptyList(), null, null);
        long seq = stepRuntimeEventService.nextSequence(eventTenantContext,
                record.getWorkflowId(), seqCounter);
        stepRuntimeEventService.publish(EventType.STEP_FAILED,
                record,
                seq,
                stepEventPayloadFactory.buildStepFailedPayload(record, errorCode, details),
                eventTenantContext);
        saveRecord(record);
        log.warn("步骤失败, tenantId={}, workflowId={}, stepId={}, errorCode={}",
                record.getTenantId(), record.getWorkflowId(), record.getStepId(), errorCode);
        return record;
    }

    /**
     * 查询步骤时间线。
     *
     * @param workflowId 工作流标识
     * @param cursor 游标
     * @param size 分页大小
     * @param tenantContext 租户上下文
     * @return 步骤时间线
     */
    public StepTimelineResponse listSteps(String workflowId,
                                          String cursor,
                                          Integer size,
                                          TenantContext tenantContext) {
        List<StepRecord> records = stepRecordRepository.findByWorkflow(tenantContext.getTenantId(), workflowId);
        if (records == null || records.isEmpty()) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "步骤时间线不存在");
        }

        ArrayList<StepRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparingLong(StepRecord::getStepSeq));

        int startIndex = 0;
        if (cursor != null && !cursor.isBlank()) {
            for (int i = 0; i < sorted.size(); i++) {
                if (cursor.equals(sorted.get(i).getStepId())) {
                    startIndex = i + 1;
                    break;
                }
            }
        }

        int pageSize = (size != null && size > 0) ? size : sorted.size();
        int endIndex = Math.min(startIndex + pageSize, sorted.size());
        List<StepRecord> page = startIndex < endIndex
                ? new ArrayList<>(sorted.subList(startIndex, endIndex))
                : new ArrayList<>();

        String nextCursor = null;
        boolean hasMore = false;
        if (endIndex < sorted.size() && endIndex > 0) {
            nextCursor = sorted.get(endIndex - 1).getStepId();
            hasMore = true;
        }

        return new StepTimelineResponse(workflowId, page, nextCursor, hasMore);
    }

    /**
     * 获取步骤记录列表，用于回放与内部查询。
     *
     * @param workflowId 工作流标识
     * @param tenantContext 租户上下文
     * @return 步骤记录列表
     */
    public List<StepRecord> getSteps(String workflowId, TenantContext tenantContext) {
        List<StepRecord> records = stepRecordRepository.findByWorkflow(tenantContext.getTenantId(), workflowId);
        if (records == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(records);
    }

    private void saveRecord(StepRecord record) {
        stepRecordRepository.save(record);
    }

    private long calcDuration(StepRecord record) {
        if (record.getStartedAt() == null || record.getCompletedAt() == null) {
            return 0;
        }
        return Duration.between(record.getStartedAt(), record.getCompletedAt()).toMillis();
    }

}
