package com.example.agent.orchestration.multiagent.dag.domain.model;

/**
 * DAG 环依赖异常。
 */
public class DagCycleDetectedException extends RuntimeException {

    public DagCycleDetectedException(String message) {
        super(message);
    }
}
