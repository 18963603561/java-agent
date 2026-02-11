package com.example.agent.orchestration.multiagent.handoff;

import com.example.agent.common.error.ErrorCodeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 内存版交接记录仓储。
 *
 * <p>用途：提供最小可用仓储实现，支持并发冲突检测与幂等键映射。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agent.multiagent.storage", name = "mode", havingValue = "inmemory")
public class InMemoryHandoffRepository implements HandoffRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryHandoffRepository.class);

    private final Map<String, HandoffRecord> records = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();

    @Override
    public HandoffRecord create(HandoffRecord record) {
        if (record == null || !StringUtils.hasText(record.getHandoffId())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "handoff record 不能为空");
        }
        writeLock.lock();
        try {
            if (records.containsKey(record.getHandoffId())) {
                throw new ErrorCodeException(HttpStatus.CONFLICT, "HANDOFF_DUPLICATE", "交接记录已存在");
            }
            if (StringUtils.hasText(record.getIdempotencyKey())) {
                String existed = idempotencyIndex.putIfAbsent(record.getIdempotencyKey(), record.getHandoffId());
                if (StringUtils.hasText(existed)) {
                    throw new ErrorCodeException(HttpStatus.CONFLICT, "HANDOFF_IDEMPOTENCY_CONFLICT", "幂等键已存在");
                }
            }
            HandoffRecord copy = copy(record);
            copy.setVersion(0L);
            Instant now = Instant.now();
            copy.setCreatedAt(copy.getCreatedAt() == null ? now : copy.getCreatedAt());
            copy.setUpdatedAt(now);
            records.put(copy.getHandoffId(), copy);
            return copy(copy);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<HandoffRecord> findById(String handoffId) {
        if (!StringUtils.hasText(handoffId)) {
            return Optional.empty();
        }
        HandoffRecord record = records.get(handoffId);
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(copy(record));
    }

    @Override
    public Optional<HandoffRecord> findByIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        String handoffId = idempotencyIndex.get(idempotencyKey);
        if (!StringUtils.hasText(handoffId)) {
            return Optional.empty();
        }
        return findById(handoffId);
    }

    @Override
    public HandoffRecord compareAndSet(HandoffRecord record, long expectedVersion) {
        if (record == null || !StringUtils.hasText(record.getHandoffId())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "handoff record 不能为空");
        }
        writeLock.lock();
        try {
            HandoffRecord current = records.get(record.getHandoffId());
            if (current == null) {
                throw new ErrorCodeException(HttpStatus.NOT_FOUND, "HANDOFF_NOT_FOUND", "交接记录不存在");
            }
            if (current.getVersion() != expectedVersion) {
                // 并发冲突：输出期望版本与实际版本，便于定位并发覆盖。
                log.warn("交接记录版本冲突, handoffId={}, expectedVersion={}, actualVersion={}",
                        record.getHandoffId(),
                        expectedVersion,
                        current.getVersion());
                throw new ErrorCodeException(HttpStatus.CONFLICT, "HANDOFF_VERSION_CONFLICT", "交接记录版本冲突");
            }
            HandoffRecord next = copy(record);
            next.setVersion(expectedVersion + 1);
            next.setCreatedAt(current.getCreatedAt());
            next.setUpdatedAt(Instant.now());
            records.put(next.getHandoffId(), next);
            return copy(next);
        } finally {
            writeLock.unlock();
        }
    }

    private HandoffRecord copy(HandoffRecord source) {
        HandoffRecord target = new HandoffRecord();
        target.setHandoffId(source.getHandoffId());
        target.setFromAgent(source.getFromAgent());
        target.setToAgent(source.getToAgent());
        target.setStatus(source.getStatus());
        target.setFailureReason(source.getFailureReason());
        target.setErrorCode(source.getErrorCode());
        target.setVersion(source.getVersion());
        target.setIdempotencyKey(source.getIdempotencyKey());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        if (source.getContext() == null) {
            target.setContext(null);
        } else {
            target.setContext(new HashMap<>(source.getContext()));
        }
        return target;
    }
}
