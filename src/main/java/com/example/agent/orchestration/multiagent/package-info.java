/**
 * 多智能体编排子域。
 * <p>职责：承载角色规划、执行路由、DAG/Supervisor 协作与事件观测能力。</p>
 * <p>依赖边界：本包禁止依赖 HTTP 控制器层；跨子域交互优先通过用例与端口接口。</p>
 * <p>禁止事项：禁止在核心流程直接硬编码指标键与事件载荷键，必须使用 observability 字典。</p>
 */
package com.example.agent.orchestration.multiagent;

