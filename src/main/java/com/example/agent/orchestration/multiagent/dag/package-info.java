/**
 * DAG 执行子域。
 * <p>职责：负责 DAG 计划构建、Actor 运行、审计诊断与回放。</p>
 * <p>分层：application 负责编排，domain 承载模型与端口，infrastructure 实现端口。</p>
 * <p>依赖规则：domain 禁止依赖 infrastructure，infrastructure 禁止承载业务编排。</p>
 */
package com.example.agent.orchestration.multiagent.dag;

