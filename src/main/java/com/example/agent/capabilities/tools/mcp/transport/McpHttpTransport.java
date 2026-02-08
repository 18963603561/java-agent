package com.example.agent.capabilities.tools.mcp.transport;

import com.example.agent.common.error.ErrorCodeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.example.agent.capabilities.tools.mcp.McpServerProperties;

/**
 * MCP HTTP 传输组件。
 */
@Component
public class McpHttpTransport {

    private static final Logger log = LoggerFactory.getLogger(McpHttpTransport.class);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public McpHttpTransport(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * POST JSON 请求。
     *
     * @param url 请求地址
     * @param body 请求体
     * @param timeoutSec 超时秒数
     * @param maxResponseBytes 响应大小限制
     * @return 响应映射
     */
    public Map<String, Object> post(String url, Object body, long timeoutSec, long maxResponseBytes) {
        byte[] payload = serializeBody(body);
        if (Schedulers.isInNonBlockingThread()) {
            if (log.isDebugEnabled()) {
                log.debug("MCP use JDK client in non-blocking thread, url={}", url);
            }
            return postWithJdkHttpClient(url, payload, timeoutSec, maxResponseBytes);
        }
        WebClient client = webClientBuilder.build();
        try {
            return client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(payload.length)
                    .bodyValue(payload)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("mcp_call_failed")
                                    .flatMap(message -> Mono.error(new ErrorCodeException(
                                            HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", message)));
                        }
                        return response.bodyToFlux(DataBuffer.class)
                                .reduceWith(ByteArrayOutputStream::new, (stream, buffer) -> {
                                    int readable = buffer.readableByteCount();
                                    if (readable > 0) {
                                        if (stream.size() + readable > maxResponseBytes) {
                                            DataBufferUtils.release(buffer);
                                            throw new ErrorCodeException(HttpStatus.PAYLOAD_TOO_LARGE,
                                                    "MCP_RESPONSE_TOO_LARGE", "mcp_response_too_large");
                                        }
                                        byte[] chunk = new byte[readable];
                                        buffer.read(chunk);
                                        stream.write(chunk, 0, readable);
                                    }
                                    DataBufferUtils.release(buffer);
                                    return stream;
                                })
                                .map(this::readResponseAsMap);
                    })
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .toFuture()
                    .get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "mcp_call_interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (isConnectionIssue(cause)) {
                log.warn("MCP WebClient failed, fallback to JDK client, url={}, causeType={}, causeMessage={}",
                        url,
                        cause != null ? cause.getClass().getName() : "unknown",
                        cause != null ? cause.getMessage() : "null",
                        cause);
                return postWithJdkHttpClient(url, payload, timeoutSec, maxResponseBytes);
            }
            if (cause instanceof ErrorCodeException error) {
                throw error;
            }
            log.error("MCP WebClient failed without fallback, url={}, causeType={}, causeMessage={}",
                    url,
                    cause != null ? cause.getClass().getName() : "unknown",
                    cause != null ? cause.getMessage() : "null",
                    cause);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "mcp_call_failed");
        } catch (RuntimeException ex) {
            if (isConnectionIssue(ex)) {
                log.warn("MCP WebClient failed, fallback to JDK client, url={}, causeType={}, causeMessage={}",
                        url,
                        ex.getClass().getName(),
                        ex.getMessage(),
                        ex);
                return postWithJdkHttpClient(url, payload, timeoutSec, maxResponseBytes);
            }
            throw ex;
        }
    }

    /**
     * 计算响应体大小上限。
     *
     * @param server 服务配置
     * @return 字节数
     */
    public long resolveMaxResponseBytes(McpServerProperties.McpServer server) {
        long configured = server != null ? server.getMaxResponseBytes() : 0;
        if (configured > 0) {
            return configured;
        }
        return 2 * 1024 * 1024L;
    }

    private byte[] serializeBody(Object body) {
        if (body == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "MCP 请求序列化失败");
        }
    }

    private Map<String, Object> postWithJdkHttpClient(String url,
                                                      byte[] payload,
                                                      long timeoutSec,
                                                      long maxResponseBytes) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readResponseBytes(response.body(), maxResponseBytes);
            if (response.statusCode() >= 400) {
                String message = body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "mcp_call_failed";
                throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", message);
            }
            if (body.length == 0) {
                return null;
            }
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "mcp_call_interrupted");
        } catch (IOException ex) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "mcp_call_failed");
        }
    }

    private byte[] readResponseBytes(InputStream inputStream, long maxResponseBytes) throws IOException {
        if (inputStream == null) {
            return new byte[0];
        }
        try (InputStream input = inputStream; ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (stream.size() + read > maxResponseBytes) {
                    throw new ErrorCodeException(HttpStatus.PAYLOAD_TOO_LARGE,
                            "MCP_RESPONSE_TOO_LARGE", "mcp_response_too_large");
                }
                stream.write(buffer, 0, read);
            }
            return stream.toByteArray();
        }
    }

    private boolean isConnectionIssue(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof java.net.SocketException
                || throwable instanceof IOException
                || throwable instanceof TimeoutException) {
            return true;
        }
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            return isConnectionIssue(cause);
        }
        return false;
    }

    private Map<String, Object> readResponseAsMap(ByteArrayOutputStream stream) {
        if (stream == null || stream.size() == 0) {
            return null;
        }
        byte[] payload = stream.toByteArray();
        try {
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 响应解析失败");
        }
    }
}

