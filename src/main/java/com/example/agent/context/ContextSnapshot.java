package com.example.agent.context;

/**
 * 上下文快照，承载六层上下文与运行状态。
 */
public class ContextSnapshot {

    /**
     * 快照标识。
     */
    private String snapshotId;

    /**
     * 运行时元信息。
     */
    private RuntimeMeta runtimeMeta;

    /**
     * 角色与边界信息。
     */
    private RoleBoundary roleBoundary;

    /**
     * 任务意图与成功标准。
     */
    private TaskIntent taskIntent;

    /**
     * 工作记忆内容。
     */
    private WorkingMemory workingMemory;

    /**
     * 领域知识引用。
     */
    private DomainKnowledge domainKnowledge;

    /**
     * 长期记忆引用。
     */
    private LongTermMemory longTermMemory;

    /**
     * 工具状态信息。
     */
    private ToolState toolState;

    /**
     * 预算状态信息。
     */
    private BudgetState budgetState;

    /**
     * 审计元数据。
     */
    private AuditMetadata auditMetadata;

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public RuntimeMeta getRuntimeMeta() {
        return runtimeMeta;
    }

    public void setRuntimeMeta(RuntimeMeta runtimeMeta) {
        this.runtimeMeta = runtimeMeta;
    }

    public RoleBoundary getRoleBoundary() {
        return roleBoundary;
    }

    public void setRoleBoundary(RoleBoundary roleBoundary) {
        this.roleBoundary = roleBoundary;
    }

    public TaskIntent getTaskIntent() {
        return taskIntent;
    }

    public void setTaskIntent(TaskIntent taskIntent) {
        this.taskIntent = taskIntent;
    }

    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    public void setWorkingMemory(WorkingMemory workingMemory) {
        this.workingMemory = workingMemory;
    }

    public DomainKnowledge getDomainKnowledge() {
        return domainKnowledge;
    }

    public void setDomainKnowledge(DomainKnowledge domainKnowledge) {
        this.domainKnowledge = domainKnowledge;
    }

    public LongTermMemory getLongTermMemory() {
        return longTermMemory;
    }

    public void setLongTermMemory(LongTermMemory longTermMemory) {
        this.longTermMemory = longTermMemory;
    }

    public ToolState getToolState() {
        return toolState;
    }

    public void setToolState(ToolState toolState) {
        this.toolState = toolState;
    }

    public BudgetState getBudgetState() {
        return budgetState;
    }

    public void setBudgetState(BudgetState budgetState) {
        this.budgetState = budgetState;
    }

    public AuditMetadata getAuditMetadata() {
        return auditMetadata;
    }

    public void setAuditMetadata(AuditMetadata auditMetadata) {
        this.auditMetadata = auditMetadata;
    }
}