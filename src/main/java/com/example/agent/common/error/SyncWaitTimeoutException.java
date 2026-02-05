package com.example.agent.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.agent.api.http.dto.TaskResponse;

/**
 * 同步等待超时异常，用于返回 202 并携带任务信息。
 */
public class SyncWaitTimeoutException extends ResponseStatusException implements ErrorCodeProvider {

    private final TaskResponse response;

    public SyncWaitTimeoutException(TaskResponse response, String reason) {
        super(HttpStatus.ACCEPTED, reason);
        this.response = response;
    }

    @Override
    public String getErrorCode() {
        return "SYNC_WAIT_TIMEOUT";
    }

    public TaskResponse getResponse() {
        return response;
    }
}
