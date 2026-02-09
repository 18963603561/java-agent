package com.example.agent.capabilities.context.assembly;

/**
 * 上下文装配器，用于生成提示词装配输入。
 */
public interface ContextAssembler {

    /**
     * 生成提示词装配输入。
     *
     * @param command 装配命令，封装快照、预算、裁剪结果和租户上下文
     * @return 装配输入
     */
    PromptAssemblyInput assemble(ContextAssemblyCommand command);
}
