package com.example.agent.capabilities.tools.mcp.session;

import com.example.agent.common.error.ErrorCodeException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.example.agent.capabilities.tools.mcp.McpServerProperties;

/**
 * MCP SSE 会话管理器。
 */
@Component
public class McpSseSessionManager {

    private static final Logger log = LoggerFactory.getLogger(McpSseSessionManager.class);
    private static final long SSE_RECONNECT_DELAY_MS = 1000;
    private static final long STATE_CLEANUP_INTERVAL_MS = 60_000;
    private static final long STATE_IDLE_EVICT_MS = 30 * 60 * 1000;
    private static final int MAX_STATE_ENTRIES = 512;
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "session[_-]?id\\\"?\\s*[:=]\\s*\\\"?([a-f0-9\\-]{36})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})",
            Pattern.CASE_INSENSITIVE);

    private final ConcurrentMap<String, SseSessionState> sseSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> sseLocks = new ConcurrentHashMap<>();
    private volatile long lastCleanupTimeMs;

    /**
     * 确保存在可用 SSE 会话。
     *
     * @param server MCP 服务配置
     * @param timeoutSeconds 超时秒数
     * @return 会话标识
     */
    public String ensureSession(McpServerProperties.McpServer server, long timeoutSeconds) {
        String sseUrl = server.getSseUrl();
        if (!StringUtils.hasText(sseUrl)) {
            return null;
        }
        long now = System.currentTimeMillis();
        cleanupExpiredStates(now);
        String sessionKey = resolveSseKey(server);
        SseSessionState state = sseSessions.computeIfAbsent(sessionKey, key -> new SseSessionState());
        Object lock = sseLocks.computeIfAbsent(sessionKey, key -> new Object());
        synchronized (lock) {
            state.lastAccessTimeMs = now;
            boolean hasSession = StringUtils.hasText(state.sessionId);
            boolean refreshNeeded = hasSession && isRefreshNeeded(server, state, now);
            if (!isWorkerAlive(state)) {
                startSseWorker(server, state, timeoutSeconds);
            }
            if (refreshNeeded) {
                log.info("MCP SSE refresh, serverId={}, url={}", server.getId(), sseUrl);
                clearSession(state);
                closeSseStream(state);
            }
        }
        String sessionId = waitForSessionId(state, timeoutSeconds);
        state.lastAccessTimeMs = System.currentTimeMillis();
        if (!StringUtils.hasText(sessionId)) {
            log.error("MCP SSE session unavailable, serverId={}, url={}", server.getId(), sseUrl);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP SSE 会话不可用");
        }
        return sessionId;
    }

    /**
     * 为 URL 附加查询参数。
     *
     * @param baseUrl 原始地址
     * @param name 参数名
     * @param value 参数值
     * @return 拼接后的地址
     */
    public String appendQueryParam(String baseUrl, String name, String value) {
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + encodedName + "=" + encodedValue;
    }

    /**
     * 构建会话键。
     *
     * @param server MCP 服务配置
     * @return 会话键
     */
    public String resolveSseKey(McpServerProperties.McpServer server) {
        String serverId = server != null ? server.getId() : "unknown";
        String sseUrl = server != null && server.getSseUrl() != null ? server.getSseUrl() : "";
        return serverId + "|" + sseUrl;
    }

    private boolean isRefreshNeeded(McpServerProperties.McpServer server, SseSessionState state, long now) {
        long refreshSeconds = server.getSessionRefreshSeconds();
        if (refreshSeconds <= 0) {
            return false;
        }
        long lastRefresh = state.lastRefreshTimeMs;
        if (lastRefresh <= 0) {
            return false;
        }
        return now - lastRefresh >= refreshSeconds * 1000;
    }

    private void clearSession(SseSessionState state) {
        state.sessionId = null;
        state.lastRefreshTimeMs = 0;
    }

    private String waitForSessionId(SseSessionState state, long waitSeconds) {
        if (state == null) {
            return null;
        }
        if (StringUtils.hasText(state.sessionId)) {
            return state.sessionId;
        }
        long waitMs = Math.max(1000, waitSeconds * 1000);
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            if (StringUtils.hasText(state.sessionId)) {
                return state.sessionId;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return state.sessionId;
            }
        }
        return state.sessionId;
    }

    private boolean isWorkerAlive(SseSessionState state) {
        Thread worker = state != null ? state.worker : null;
        return worker != null && worker.isAlive();
    }

    /**
     * 清理长期未访问的会话状态，避免状态表无限增长。
     *
     * <p>策略：按固定周期触发；当状态闲置超过阈值或总数超限时，执行回收。</p>
     *
     * @param now 当前时间戳
     */
    private void cleanupExpiredStates(long now) {
        if (now - lastCleanupTimeMs < STATE_CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupTimeMs = now;
        for (Map.Entry<String, SseSessionState> entry : sseSessions.entrySet()) {
            String key = entry.getKey();
            SseSessionState state = entry.getValue();
            if (state == null) {
                removeState(key, null);
                continue;
            }
            long idleMs = Math.max(0L, now - state.lastAccessTimeMs);
            boolean idleExpired = idleMs > STATE_IDLE_EVICT_MS;
            boolean overflow = sseSessions.size() > MAX_STATE_ENTRIES;
            if (!idleExpired && !overflow) {
                continue;
            }
            removeState(key, state);
            if (log.isDebugEnabled()) {
                log.debug("MCP SSE state evicted, key={}, idleMs={}, overflow={}", key, idleMs, overflow);
            }
        }
    }

    private void removeState(String key, SseSessionState state) {
        if (state != null) {
            Thread worker = state.worker;
            if (worker != null) {
                worker.interrupt();
            }
            closeSseStream(state);
            clearSession(state);
        }
        sseSessions.remove(key);
        sseLocks.remove(key);
    }

    private void startSseWorker(McpServerProperties.McpServer server,
                                SseSessionState state,
                                long timeoutSeconds) {
        if (state == null || server == null) {
            return;
        }
        if (isWorkerAlive(state)) {
            return;
        }
        String threadName = "mcp-sse-" + (StringUtils.hasText(server.getId()) ? server.getId() : UUID.randomUUID());
        Thread worker = new Thread(() -> runSseLoop(server, state, timeoutSeconds), threadName);
        worker.setDaemon(true);
        state.worker = worker;
        worker.start();
    }

    private void runSseLoop(McpServerProperties.McpServer server,
                            SseSessionState state,
                            long timeoutSeconds) {
        String serverId = server.getId();
        String sseUrl = server.getSseUrl();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                log.info("MCP SSE connect start, serverId={}, url={}", serverId, sseUrl);
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sseUrl))
                        .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .GET()
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    log.warn("MCP SSE status error, serverId={}, url={}, status={}",
                            serverId, sseUrl, response.statusCode());
                    sleepBeforeReconnect();
                    continue;
                }
                try (InputStream stream = response.body();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    setSseStream(state, stream);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!StringUtils.hasText(line)) {
                            continue;
                        }
                        String sessionId = extractSessionId(line);
                        if (StringUtils.hasText(sessionId)) {
                            updateSession(state, serverId, sessionId);
                        }
                    }
                } finally {
                    closeSseStream(state);
                }
                clearSession(state);
                log.warn("MCP SSE disconnected, serverId={}, url={}", serverId, sseUrl);
            } catch (Exception ex) {
                log.warn("MCP SSE failed, serverId={}, url={}", serverId, sseUrl, ex);
            }
            sleepBeforeReconnect();
        }
    }

    private void updateSession(SseSessionState state, String serverId, String sessionId) {
        if (state == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        boolean changed = !sessionId.equals(state.sessionId);
        state.sessionId = sessionId;
        state.lastRefreshTimeMs = System.currentTimeMillis();
        if (changed) {
            log.info("MCP SSE session updated, serverId={}, sessionId={}", serverId, sessionId);
        }
    }

    private void setSseStream(SseSessionState state, InputStream stream) {
        if (state == null) {
            return;
        }
        synchronized (state.streamLock) {
            state.stream = stream;
        }
    }

    private void closeSseStream(SseSessionState state) {
        if (state == null) {
            return;
        }
        InputStream stream;
        synchronized (state.streamLock) {
            stream = state.stream;
            state.stream = null;
        }
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ex) {
                log.warn("MCP SSE stream close failed", ex);
            }
        }
    }

    private String extractSessionId(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        String text = line.trim();
        if (text.startsWith("data:")) {
            text = text.substring("data:".length()).trim();
        }
        Matcher matcher = SESSION_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = UUID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void sleepBeforeReconnect() {
        try {
            Thread.sleep(SSE_RECONNECT_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * SSE 会话状态。
     */
    private static class SseSessionState {
        /**
         * 当前 sessionId。
         */
        private volatile String sessionId;
        /**
         * 最近刷新时间戳。
         */
        private volatile long lastRefreshTimeMs;
        /**
         * 最近访问时间戳。
         */
        private volatile long lastAccessTimeMs;
        /**
         * SSE 线程。
         */
        private volatile Thread worker;
        /**
         * SSE 流对象。
         */
        private volatile InputStream stream;
        /**
         * SSE 流锁。
         */
        private final Object streamLock = new Object();
    }
}
