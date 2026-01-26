package com.example.agent.multiagent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 交接服务，负责多智能体上下文传递。
 */
@Service
public class HandoffService {

    private static final Logger log = LoggerFactory.getLogger(HandoffService.class);

    /**
     * 执行交接。
     *
     * @param request 交接请求
     * @return 交接结果
     */
    public HandoffResult handoff(HandoffRequest request) {
        HandoffRecord record = new HandoffRecord();
        record.setHandoffId(UUID.randomUUID().toString());
        record.setFromAgent(request.getFromAgent());
        record.setToAgent(request.getToAgent());
        record.setCreatedAt(Instant.now());
        log.info("交接完成, from={}, to={}", request.getFromAgent(), request.getToAgent());
        return new HandoffResult("COMPLETED", request.getContext() == null ? Map.of() : request.getContext());
    }
}
