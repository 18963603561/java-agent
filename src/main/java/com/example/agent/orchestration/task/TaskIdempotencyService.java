package com.example.agent.orchestration.task;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 任务幂等服务。
 * <p>用途：收敛幂等键归一化、本地并发锁、幂等映射查询与写入逻辑。
 */
@Service
public class TaskIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(TaskIdempotencyService.class);

    private final TaskRepository taskRepository;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, Object> idempotencyLocks = new ConcurrentHashMap<>();

    @Value("${agent.idempotency.redis-enabled:false}")
    private boolean redisIdempotencyEnabled;

    @Value("${agent.idempotency.ttl-seconds:86400}")
    private long idempotencyTtlSeconds;

    public TaskIdempotencyService(TaskRepository taskRepository,
                                  ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.taskRepository = taskRepository;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 归一化幂等键。
     */
    public String normalizeIdempotencyKey(String rawKey) {
        if (!StringUtils.hasText(rawKey)) {
            return null;
        }
        String trimmed = rawKey.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    /**
     * 在本地锁保护下执行幂等相关逻辑。
     */
    public <T> T executeWithLocalLock(String tenantId, String idempotencyKey, Supplier<T> supplier) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return supplier.get();
        }
        String lockKey = buildIdempotencyKey(tenantId, idempotencyKey);
        Object lock = idempotencyLocks.computeIfAbsent(lockKey, key -> new Object());
        synchronized (lock) {
            try {
                return supplier.get();
            } finally {
                idempotencyLocks.remove(lockKey, lock);
            }
        }
    }

    /**
     * 查找幂等命中任务。
     */
    public TaskRecord findIdempotent(String tenantId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisIdempotencyEnabled && redisTemplate != null) {
            String redisKey = buildIdempotencyKey(tenantId, idempotencyKey);
            String taskId = redisTemplate.opsForValue().get(redisKey);
            if (StringUtils.hasText(taskId)) {
                TaskRecord record = taskRepository.findById(tenantId, taskId);
                if (record != null) {
                    return record;
                }
            }
        }
        return taskRepository.findByIdempotencyKey(tenantId, idempotencyKey);
    }

    /**
     * 保存幂等映射。
     */
    public void storeIdempotency(String tenantId, String idempotencyKey, String taskId) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisIdempotencyEnabled && redisTemplate != null) {
            String redisKey = buildIdempotencyKey(tenantId, idempotencyKey);
            redisTemplate.opsForValue().set(redisKey, taskId, Duration.ofSeconds(idempotencyTtlSeconds));
            log.debug("写入幂等映射, tenantId={}, taskId={}", tenantId, taskId);
        }
    }

    /**
     * 构建幂等缓存键。
     */
    public String buildIdempotencyKey(String tenantId, String idempotencyKey) {
        return "idempotency:task:" + tenantId + ":" + idempotencyKey;
    }
}

