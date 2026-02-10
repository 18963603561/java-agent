package com.example.agent.common.error;

import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 同步等待超时异常。
 * <p>用途：在同步等待降级为异步时返回 202 与任务基础信息。
 */
public class SyncWaitTimeoutException extends ResponseStatusException implements ErrorCodeProvider {

    private final TaskSubmissionResult result;

    public SyncWaitTimeoutException(TaskSubmissionResult result, String reason) {
        super(HttpStatus.ACCEPTED, reason);
        this.result = result;
    }

    @Override
    public String getErrorCode() {
        return "SYNC_WAIT_TIMEOUT";
    }

    public TaskSubmissionResult getResult() {
        return result;
    }
}

