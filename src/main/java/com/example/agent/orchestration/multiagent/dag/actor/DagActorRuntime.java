package com.example.agent.orchestration.multiagent.dag.actor;

import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.dag.DagNode;
import com.example.agent.orchestration.multiagent.dag.DagPlan;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagDistributedProperties;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMailboxDispatcher;
import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagShardRouter;
import com.example.agent.orchestration.multiagent.dag.actor.runtime.DagDependencyPropagationService;
import com.example.agent.orchestration.multiagent.dag.actor.runtime.DagNodeExecutionService;
import com.example.agent.orchestration.multiagent.dag.actor.runtime.DagRunFinalizeService;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagMessageDedupRepository;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeLeaseService;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagNodeRuntimeSnapshot;
import com.example.agent.orchestration.multiagent.dag.actor.state.DagRuntimeStateRepository;
import com.example.agent.orchestration.multiagent.dag.actor.state.InMemoryDagMessageDedupRepository;
import com.example.agent.orchestration.multiagent.dag.actor.state.InMemoryDagRuntimeStateRepository;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagBackpressureRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import com.example.agent.orchestration.multiagent.dag.audit.InMemoryDagAuditRepository;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.orchestration.multiagent.model.ReasonCode;
import com.example.agent.orchestration.multiagent.model.RunStatus;
import com.example.agent.orchestration.multiagent.supervisor.FailurePropagationPolicy;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * DAG Actor 运行时。
 *
 * <p>用途：负责 DAG 运行主循环编排，只保留调度层逻辑。
 * 节点执行、依赖传播、运行收尾下沉到独立服务，避免单类膨胀。</p>
 */
@Component
public class DagActorRuntime {

    private static final Logger log = LoggerFactory.getLogger(DagActorRuntime.class);

    private final MultiAgentEventPublisher eventPublisher;
    private final DagAuditService dagAuditService;
    private final DagSupervisorPolicy dagSupervisorPolicy;
    private final DagBackpressurePolicy dagBackpressurePolicy;
    private final DagMailboxDispatcher dagMailboxDispatcher;
    private final DagDistributedProperties dagDistributedProperties;
    private final DagRuntimeStateRepository dagRuntimeStateRepository;
    private final DagMessageDedupRepository dagMessageDedupRepository;
    private final DagNodeLeaseService dagNodeLeaseService;
    private final DagNodeExecutionService dagNodeExecutionService;
    private final DagRunFinalizeService dagRunFinalizeService;

    @Autowired
    public DagActorRuntime(WorkspaceSyncService workspaceSyncService,
                           MultiAgentEventPublisher eventPublisher,
                           DagAuditService dagAuditService,
                           DagMailboxDispatcher dagMailboxDispatcher,
                           DagShardRouter dagShardRouter,
                           DagDistributedProperties dagDistributedProperties,
                           DagRuntimeStateRepository dagRuntimeStateRepository,
                           DagMessageDedupRepository dagMessageDedupRepository,
                           DagNodeLeaseService dagNodeLeaseService,
                           DagSupervisorPolicy dagSupervisorPolicy,
                           DagBackpressurePolicy dagBackpressurePolicy) {
        this.eventPublisher = eventPublisher;
        this.dagAuditService = dagAuditService;
        this.dagMailboxDispatcher = dagMailboxDispatcher;
        this.dagDistributedProperties = dagDistributedProperties;
        this.dagRuntimeStateRepository = dagRuntimeStateRepository;
        this.dagMessageDedupRepository = dagMessageDedupRepository;
        this.dagNodeLeaseService = dagNodeLeaseService;
        this.dagSupervisorPolicy = dagSupervisorPolicy;
        this.dagBackpressurePolicy = dagBackpressurePolicy;
        DagDependencyPropagationService propagationService = new DagDependencyPropagationService(
                dagShardRouter,
                dagDistributedProperties,
                dagMailboxDispatcher,
                dagAuditService);
        this.dagNodeExecutionService = new DagNodeExecutionService(
                workspaceSyncService,
                eventPublisher,
                dagAuditService,
                dagSupervisorPolicy,
                dagBackpressurePolicy,
                dagDistributedProperties,
                dagNodeLeaseService,
                dagRuntimeStateRepository,
                propagationService);
        this.dagRunFinalizeService = new DagRunFinalizeService(eventPublisher,
                dagAuditService,
                dagSupervisorPolicy);
    }

    /**
     * 轻量测试构造器。
     */
    public DagActorRuntime(WorkspaceSyncService workspaceSyncService,
                           MultiAgentEventPublisher eventPublisher) {
        DagDistributedProperties distributedProperties = new DagDistributedProperties();
        DagMailboxDispatcher dispatcher = new DagMailboxDispatcher(distributedProperties,
                new com.example.agent.orchestration.multiagent.dag.actor.distributed.InProcessDagMailboxTransport(),
                new com.example.agent.streaming.observability.MetricsPublisher(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                new com.example.agent.orchestration.multiagent.dag.actor.recovery.InMemoryDagDeadLetterRepository());
        DagShardRouter shardRouter = new DagShardRouter();
        DagAuditService auditService = new DagAuditService(new InMemoryDagAuditRepository());
        DagRuntimeStateRepository stateRepository = new InMemoryDagRuntimeStateRepository();
        DagMessageDedupRepository dedupRepository = new InMemoryDagMessageDedupRepository();
        DagNodeLeaseService leaseService = new DagNodeLeaseService();
        DagSupervisorPolicy supervisorPolicy = new DagSupervisorPolicy(2,
                1,
                30000L,
                FailurePropagationPolicy.PARTIAL_SUCCESS);
        DagBackpressurePolicy backpressurePolicy = new DagBackpressurePolicy(4,
                2048,
                128,
                30000L,
                200L,
                5000L);
        this.eventPublisher = eventPublisher;
        this.dagAuditService = auditService;
        this.dagMailboxDispatcher = dispatcher;
        this.dagDistributedProperties = distributedProperties;
        this.dagRuntimeStateRepository = stateRepository;
        this.dagMessageDedupRepository = dedupRepository;
        this.dagNodeLeaseService = leaseService;
        this.dagSupervisorPolicy = supervisorPolicy;
        this.dagBackpressurePolicy = backpressurePolicy;
        DagDependencyPropagationService propagationService = new DagDependencyPropagationService(
                shardRouter,
                distributedProperties,
                dispatcher,
                auditService);
        this.dagNodeExecutionService = new DagNodeExecutionService(
                workspaceSyncService,
                eventPublisher,
                auditService,
                supervisorPolicy,
                backpressurePolicy,
                distributedProperties,
                leaseService,
                stateRepository,
                propagationService);
        this.dagRunFinalizeService = new DagRunFinalizeService(eventPublisher,
                auditService,
                supervisorPolicy);
    }

    /**
     * 测试构造器，允许覆盖策略参数。
     */
    public DagActorRuntime(WorkspaceSyncService workspaceSyncService,
                           MultiAgentEventPublisher eventPublisher,
                           DagSupervisorPolicy dagSupervisorPolicy,
                           DagBackpressurePolicy dagBackpressurePolicy) {
        DagDistributedProperties distributedProperties = new DagDistributedProperties();
        DagMailboxDispatcher dispatcher = new DagMailboxDispatcher(distributedProperties,
                new com.example.agent.orchestration.multiagent.dag.actor.distributed.InProcessDagMailboxTransport(),
                new com.example.agent.streaming.observability.MetricsPublisher(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                new com.example.agent.orchestration.multiagent.dag.actor.recovery.InMemoryDagDeadLetterRepository());
        DagShardRouter shardRouter = new DagShardRouter();
        DagAuditService auditService = new DagAuditService(new InMemoryDagAuditRepository());
        DagRuntimeStateRepository stateRepository = new InMemoryDagRuntimeStateRepository();
        DagMessageDedupRepository dedupRepository = new InMemoryDagMessageDedupRepository();
        DagNodeLeaseService leaseService = new DagNodeLeaseService();
        this.eventPublisher = eventPublisher;
        this.dagAuditService = auditService;
        this.dagMailboxDispatcher = dispatcher;
        this.dagDistributedProperties = distributedProperties;
        this.dagRuntimeStateRepository = stateRepository;
        this.dagMessageDedupRepository = dedupRepository;
        this.dagNodeLeaseService = leaseService;
        this.dagSupervisorPolicy = dagSupervisorPolicy;
        this.dagBackpressurePolicy = dagBackpressurePolicy;
        DagDependencyPropagationService propagationService = new DagDependencyPropagationService(
                shardRouter,
                distributedProperties,
                dispatcher,
                auditService);
        this.dagNodeExecutionService = new DagNodeExecutionService(
                workspaceSyncService,
                eventPublisher,
                auditService,
                dagSupervisorPolicy,
                dagBackpressurePolicy,
                distributedProperties,
                leaseService,
                stateRepository,
                propagationService);
        this.dagRunFinalizeService = new DagRunFinalizeService(eventPublisher,
                auditService,
                dagSupervisorPolicy);
    }

    /**
     * 运行 DAG Actor。
     */
    public DagActorRuntimeResult run(String workflowId,
                                     TenantContext tenantContext,
                                     AtomicLong seqCounter,
                                     DagPlan dagPlan) {
        if (dagPlan == null || dagPlan.getNodes().isEmpty()) {
            String dagRunId = buildDagRunId(workflowId, seqCounter);
            return DagActorRuntimeResult.completed(dagRunId,
                    List.of(),
                    List.of(),
                    RunStatus.COMPLETED.code(),
                    List.of());
        }

        String dagRunId = buildDagRunId(workflowId, seqCounter);
        Instant runStartedAt = Instant.now();
        DagRunAuditRecord runRecord = dagAuditService.buildRunStartRecord(dagRunId,
                workflowId,
                tenantContext == null ? null : tenantContext.getTenantId(),
                dagSupervisorPolicy.getFailurePropagationPolicy().name(),
                dagSupervisorPolicy.getMaxFailures(),
                dagSupervisorPolicy.getMaxRetriesPerNode(),
                runStartedAt);
        dagAuditService.saveRunRecord(runRecord);

        Map<String, DagNodeActor> actorMap = new HashMap<>();
        Map<String, DagNodeRuntimeState> stateMap = new ConcurrentHashMap<>();
        Map<String, List<String>> downstreamMap = dagPlan.getDownstream().isEmpty()
                ? buildDownstreamMap(dagPlan.getNodes())
                : dagPlan.getDownstream();
        ConcurrentLinkedQueue<String> readyQueue = new ConcurrentLinkedQueue<>();
        Set<String> readySet = ConcurrentHashMap.newKeySet();
        List<String> failures = new ArrayList<>();
        List<String> completedNodes = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicLong messageVersion = new AtomicLong(0L);
        AtomicLong activeNodes = new AtomicLong(0L);
        AtomicLong totalFailureCounter = new AtomicLong(0L);

        for (DagNode node : dagPlan.getNodes()) {
            int initialDependencies = dagPlan.getInDegree().getOrDefault(node.getNodeId(), node.getDependsOn().size());
            DagNodeRuntimeState runtimeState = new DagNodeRuntimeState(node.getNodeId(), initialDependencies);
            stateMap.put(node.getNodeId(), runtimeState);
            DagNodeActor actor = buildActor(workflowId,
                    dagRunId,
                    tenantContext,
                    seqCounter,
                    node,
                    runtimeState,
                    readyQueue,
                    readySet,
                    completedNodes,
                    failures,
                    totalFailureCounter,
                    activeNodes,
                    downstreamMap,
                    actorMap,
                    messageVersion);
            actorMap.put(node.getNodeId(), actor);
            saveRuntimeSnapshot(dagRunId,
                    workflowId,
                    node.getNodeId(),
                    runtimeState,
                    0,
                    1L);
            dagMailboxDispatcher.registerHandler(dagRunId, node.getNodeId(), actor);
            if (runtimeState.getRemainingDependencies() == 0) {
                runtimeState.forceReadyWhenNoDependencies();
                enqueueReadyNode(readyQueue,
                        readySet,
                        dagRunId,
                        node.getNodeId(),
                        tenantContext,
                        workflowId,
                        seqCounter);
            }
        }

        int workerSize = Math.max(1,
                Math.min(dagBackpressurePolicy.getMaxActiveNodes(), Math.max(1, actorMap.size())));
        ExecutorService executorService = Executors.newFixedThreadPool(workerSize);
        try {
            while (!readyQueue.isEmpty()) {
                if (shouldAbortByFailurePolicy(totalFailureCounter.get())) {
                    log.warn("DAG运行提前终止, workflowId={}, failedCount={}, maxFailures={}, policy={}",
                            workflowId,
                            totalFailureCounter.get(),
                            dagSupervisorPolicy.getMaxFailures(),
                            dagSupervisorPolicy.getFailurePropagationPolicy());
                    break;
                }
                List<Future<?>> tasks = new ArrayList<>();
                List<String> batch = new ArrayList<>();
                while (!readyQueue.isEmpty() && tasks.size() < dagBackpressurePolicy.getMaxActiveNodes()) {
                    String nodeId = readyQueue.poll();
                    if (!StringUtils.hasText(nodeId)) {
                        continue;
                    }
                    readySet.remove(nodeId);
                    DagNodeActor actor = actorMap.get(nodeId);
                    if (actor == null) {
                        continue;
                    }
                    DagNodeRuntimeState runtimeState = stateMap.get(nodeId);
                    if (runtimeState == null || runtimeState.getStatus() != DagNodeExecutionStatus.READY) {
                        continue;
                    }
                    batch.add(nodeId);
                    tasks.add(executorService.submit(() -> {
                        activeNodes.incrementAndGet();
                        try {
                            actor.processMessage(new DagMessage(DagMessageType.NODE_START,
                                    workflowId,
                                    "runtime",
                                    nodeId,
                                    "",
                                    messageVersion.incrementAndGet(),
                                    Map.of()));
                        } finally {
                            activeNodes.decrementAndGet();
                        }
                    }));
                }
                for (Future<?> task : tasks) {
                    task.get(Math.max(1, dagSupervisorPolicy.getNodeTimeoutMs() / 1000L), TimeUnit.SECONDS);
                }
                log.info("DAG批次执行完成, workflowId={}, batchSize={}, batchNodes={}",
                        workflowId,
                        batch.size(),
                        batch);
                if (tasks.isEmpty() && !readyQueue.isEmpty()) {
                    long delayMs = jitterBackoff(dagBackpressurePolicy.getBackoffMinMs(),
                            dagBackpressurePolicy.getBackoffMaxMs());
                    eventPublisher.publishDagNodeWaiting(tenantContext,
                            workflowId,
                            seqCounter,
                            "runtime",
                            "runtime",
                            "ready_queue_throttled",
                            delayMs,
                            delayMs,
                            1,
                            dagRunId,
                            ReasonCode.READY_QUEUE_THROTTLED.code());
                    sleepQuietly(delayMs);
                }
                dagMailboxDispatcher.dispatchDagRun(dagRunId);
            }
        } catch (TimeoutException timeoutException) {
            String reason = "dag_actor_timeout:" + timeoutException.getMessage();
            failures.add(reason);
            eventPublisher.publishTeamStatus(tenantContext,
                    workflowId,
                    seqCounter,
                    RunStatus.FAILED.code(),
                    Map.of("reason", reason,
                            "failedCount", totalFailureCounter.get(),
                            "maxFailures", dagSupervisorPolicy.getMaxFailures()));
            log.error("DAG Actor执行超时, workflowId={}", workflowId, timeoutException);
        } catch (Exception exception) {
            String reason = "dag_actor_exception:" + exception.getMessage();
            failures.add(reason);
            eventPublisher.publishTeamStatus(tenantContext,
                    workflowId,
                    seqCounter,
                    RunStatus.FAILED.code(),
                    Map.of("reason", reason,
                            "failedCount", totalFailureCounter.get(),
                            "maxFailures", dagSupervisorPolicy.getMaxFailures()));
            log.error("DAG Actor执行异常, workflowId={}", workflowId, exception);
        } finally {
            executorService.shutdownNow();
            for (DagNode node : dagPlan.getNodes()) {
                dagMailboxDispatcher.unregisterHandler(dagRunId, node.getNodeId());
                dagNodeLeaseService.release(dagRunId, node.getNodeId(), dagDistributedProperties.getInstanceId());
            }
            dagMessageDedupRepository.cleanupByDagRun(dagRunId);
        }

        return dagRunFinalizeService.finalizeRun(dagRunId,
                workflowId,
                tenantContext,
                seqCounter,
                dagPlan,
                stateMap,
                completedNodes,
                failures,
                runRecord);
    }

    private DagNodeActor buildActor(String workflowId,
                                    String dagRunId,
                                    TenantContext tenantContext,
                                    AtomicLong seqCounter,
                                    DagNode node,
                                    DagNodeRuntimeState runtimeState,
                                    ConcurrentLinkedQueue<String> readyQueue,
                                    Set<String> readySet,
                                    List<String> completedNodes,
                                    List<String> failures,
                                    AtomicLong totalFailureCounter,
                                    AtomicLong activeNodes,
                                    Map<String, List<String>> downstreamMap,
                                    Map<String, DagNodeActor> actorMap,
                                    AtomicLong messageVersion) {
        return new DagNodeActor(node,
                new DagMailbox(node.getNodeId(), dagBackpressurePolicy.getMailboxCapacity()),
                runtimeState,
                () -> dagNodeExecutionService.executeNode(new DagNodeExecutionService.NodeExecutionContext(
                        workflowId,
                        dagRunId,
                        tenantContext,
                        seqCounter,
                        node,
                        runtimeState,
                        completedNodes,
                        failures,
                        totalFailureCounter,
                        activeNodes,
                        downstreamMap,
                        actorMap,
                        messageVersion)),
                readyNodeId -> markReadyAndEnqueue(readyNodeId,
                        actorMap,
                        readyQueue,
                        readySet,
                        dagRunId,
                        tenantContext,
                        workflowId,
                        seqCounter),
                dagRunId,
                dagMessageDedupRepository,
                dagDistributedProperties);
    }

    private void markReadyAndEnqueue(String nodeId,
                                     Map<String, DagNodeActor> actorMap,
                                     ConcurrentLinkedQueue<String> readyQueue,
                                     Set<String> readySet,
                                     String dagRunId,
                                     TenantContext tenantContext,
                                     String workflowId,
                                     AtomicLong seqCounter) {
        DagNodeActor actor = actorMap.get(nodeId);
        if (actor == null) {
            return;
        }
        DagNodeRuntimeState state = actor.getRuntimeState();
        if (state.tryReady()) {
            enqueueReadyNode(readyQueue,
                    readySet,
                    dagRunId,
                    nodeId,
                    tenantContext,
                    workflowId,
                    seqCounter);
        }
    }

    private boolean shouldAbortByFailurePolicy(long failedCount) {
        FailurePropagationPolicy policy = dagSupervisorPolicy.getFailurePropagationPolicy();
        if (policy == FailurePropagationPolicy.FAIL_FAST) {
            return failedCount > 0;
        }
        return failedCount >= dagSupervisorPolicy.getMaxFailures();
    }

    private void enqueueReadyNode(ConcurrentLinkedQueue<String> readyQueue,
                                  Set<String> readySet,
                                  String dagRunId,
                                  String nodeId,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter) {
        if (!readySet.add(nodeId)) {
            return;
        }
        if (readyQueue.size() >= dagBackpressurePolicy.getMaxReadyQueueSize()) {
            long delayMs = jitterBackoff(dagBackpressurePolicy.getBackoffMinMs(),
                    dagBackpressurePolicy.getBackoffMaxMs());
            saveBackpressureAuditRecord(dagRunId,
                    workflowId,
                    nodeId,
                    "ready_queue_full",
                    readyQueue.size(),
                    dagBackpressurePolicy.getMaxReadyQueueSize(),
                    delayMs);
            eventPublisher.publishDagBackpressureApplied(tenantContext,
                    workflowId,
                    seqCounter,
                    nodeId,
                    "ready_queue_full",
                    readyQueue.size(),
                    dagBackpressurePolicy.getMaxReadyQueueSize(),
                    delayMs,
                    dagRunId,
                    1,
                    "READY_QUEUE_FULL");
            eventPublisher.publishDagNodeWaiting(tenantContext,
                    workflowId,
                    seqCounter,
                    nodeId,
                    nodeId,
                    "ready_queue_backoff",
                    delayMs,
                    delayMs,
                    1,
                    dagRunId,
                    "READY_QUEUE_BACKOFF");
            sleepQuietly(delayMs);
        }
        readyQueue.offer(nodeId);
    }

    private long jitterBackoff(long min, long max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private void saveBackpressureAuditRecord(String dagRunId,
                                             String workflowId,
                                             String nodeId,
                                             String reason,
                                             int queueSize,
                                             int capacity,
                                             long delayMs) {
        DagBackpressureRecord record = new DagBackpressureRecord();
        record.setDagRunId(dagRunId);
        record.setWorkflowId(workflowId);
        record.setNodeId(nodeId);
        record.setReason(reason);
        record.setQueueSize(queueSize);
        record.setCapacity(capacity);
        record.setDelayMs(delayMs);
        record.setOccurredAt(Instant.now());
        dagAuditService.saveBackpressureRecord(record);
    }

    private String buildDagRunId(String workflowId, AtomicLong seqCounter) {
        long seed = seqCounter == null ? System.nanoTime() : seqCounter.incrementAndGet();
        String safeWorkflowId = StringUtils.hasText(workflowId) ? workflowId : "workflow";
        return safeWorkflowId + ":dag:" + seed;
    }

    private void saveRuntimeSnapshot(String dagRunId,
                                     String workflowId,
                                     String nodeId,
                                     DagNodeRuntimeState runtimeState,
                                     int attempt,
                                     long version) {
        if (runtimeState == null) {
            return;
        }
        DagNodeRuntimeSnapshot snapshot = new DagNodeRuntimeSnapshot();
        snapshot.setDagRunId(dagRunId);
        snapshot.setWorkflowId(workflowId);
        snapshot.setNodeId(nodeId);
        snapshot.setStatus(runtimeState.getStatus() == null ? null : runtimeState.getStatus().name());
        snapshot.setRemainingDependencies(runtimeState.getRemainingDependencies());
        snapshot.setAttempt(attempt);
        snapshot.setVersion(version);
        snapshot.setUpdatedAt(Instant.now());
        dagRuntimeStateRepository.save(snapshot);
    }

    private Map<String, List<String>> buildDownstreamMap(List<DagNode> nodes) {
        Map<String, List<String>> downstreamMap = new HashMap<>();
        for (DagNode node : nodes) {
            downstreamMap.putIfAbsent(node.getNodeId(), new ArrayList<>());
        }
        for (DagNode node : nodes) {
            for (String dependency : node.getDependsOn()) {
                downstreamMap.computeIfAbsent(dependency, ignore -> new ArrayList<>()).add(node.getNodeId());
            }
        }
        return downstreamMap;
    }

    private void sleepQuietly(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * DAG Actor 运行结果。
     */
    public static class DagActorRuntimeResult {

        private final String dagRunId;
        private final String status;
        private final List<String> dagOrder;
        private final List<Map<String, Object>> dagNodes;
        private final List<String> failures;

        public DagActorRuntimeResult(String dagRunId,
                                     String status,
                                     List<String> dagOrder,
                                     List<Map<String, Object>> dagNodes,
                                     List<String> failures) {
            this.dagRunId = dagRunId;
            this.status = status;
            this.dagOrder = dagOrder == null ? List.of() : List.copyOf(dagOrder);
            this.dagNodes = dagNodes == null ? List.of() : List.copyOf(dagNodes);
            this.failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public static DagActorRuntimeResult completed(String dagRunId,
                                                      List<String> dagOrder,
                                                      List<Map<String, Object>> dagNodes,
                                                      String status,
                                                      List<String> failures) {
            return new DagActorRuntimeResult(dagRunId, status, dagOrder, dagNodes, failures);
        }

        public String getDagRunId() {
            return dagRunId;
        }

        public String getStatus() {
            return status;
        }

        public List<String> getDagOrder() {
            return dagOrder;
        }

        public List<Map<String, Object>> getDagNodes() {
            return dagNodes;
        }

        public List<String> getFailures() {
            return failures;
        }
    }
}
