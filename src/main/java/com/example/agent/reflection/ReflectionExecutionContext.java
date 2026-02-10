package com.example.agent.reflection;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.reflection.model.ReflectionContext;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 反思执行上下文。
 *
 * <p>用途：统一承载反思阶段的输入参数，避免多参数方法签名扩散。</p>
 */
public class ReflectionExecutionContext {

    /**
     * 步骤定义。
     */
    private final StepSpec step;

    /**
     * 步骤输出。
     */
    private final StepExecutionOutput output;

    /**
     * 租户上下文。
     */
    private final TenantContext tenantContext;

    /**
     * 当前尝试次数。
     */
    private final int attempt;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 事件序列计数器。
     */
    private final AtomicLong seqCounter;

    /**
     * 强类型反思上下文。
     */
    private final ReflectionContext reflectionContext;

    private ReflectionExecutionContext(Builder builder) {
        this.step = builder.step;
        this.output = builder.output;
        this.tenantContext = builder.tenantContext;
        this.attempt = builder.attempt;
        this.workflowId = builder.workflowId;
        this.seqCounter = builder.seqCounter;
        this.reflectionContext = builder.reflectionContext;
    }

    public static Builder builder() {
        return new Builder();
    }

    public StepSpec getStep() {
        return step;
    }

    public StepExecutionOutput getOutput() {
        return output;
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public AtomicLong getSeqCounter() {
        return seqCounter;
    }

    public ReflectionContext getReflectionContext() {
        return reflectionContext;
    }

    /**
     * 反思执行上下文构建器。
     */
    public static class Builder {

        private StepSpec step;
        private StepExecutionOutput output;
        private TenantContext tenantContext;
        private int attempt;
        private String workflowId;
        private AtomicLong seqCounter;
        private ReflectionContext reflectionContext;

        public Builder step(StepSpec step) {
            this.step = step;
            return this;
        }

        public Builder output(StepExecutionOutput output) {
            this.output = output;
            return this;
        }

        public Builder tenantContext(TenantContext tenantContext) {
            this.tenantContext = tenantContext;
            return this;
        }

        public Builder attempt(int attempt) {
            this.attempt = attempt;
            return this;
        }

        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder seqCounter(AtomicLong seqCounter) {
            this.seqCounter = seqCounter;
            return this;
        }

        public Builder reflectionContext(ReflectionContext reflectionContext) {
            this.reflectionContext = reflectionContext;
            return this;
        }

        public ReflectionExecutionContext build() {
            return new ReflectionExecutionContext(this);
        }
    }
}
