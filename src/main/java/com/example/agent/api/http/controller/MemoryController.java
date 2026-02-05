package com.example.agent.api.http.controller;

import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.capabilities.memory.CompressionRequest;
import com.example.agent.capabilities.memory.MemoryQuery;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.MemorySearchResult;
import com.example.agent.capabilities.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 记忆接口控制器。
 */
@RestController
@Validated
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryStore memoryStore;
    private final AuthService authService;

    public MemoryController(MemoryStore memoryStore, AuthService authService) {
        this.memoryStore = memoryStore;
        this.authService = authService;
    }

    /**
     * 保存记忆。
     *
     * @param record 记忆记录
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 保存结果
     */
    @PostMapping("/api/v1/memory/save")
    public ApiResponse<MemoryRecord> save(@RequestBody MemoryRecord record,
                                          @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        MemoryRecord saved = memoryStore.save(record, tenantContext);
        log.info("记忆保存完成, tenantId={}, memoryId={}",
                tenantContext.getTenantId(), saved.getMemoryId());
        return ApiResponse.success(saved, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 搜索记忆。
     *
     * @param query 查询请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 搜索结果
     */
    @PostMapping("/api/v1/memory/search")
    public ApiResponse<MemorySearchResult> search(@RequestBody MemoryQuery query,
                                                  @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                  ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        MemorySearchResult result = memoryStore.search(query, tenantContext);
        log.info("记忆搜索完成, tenantId={}, count={}",
                tenantContext.getTenantId(), result.getRecords().size());
        return ApiResponse.success(result, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 压缩记忆。
     *
     * @param request 压缩请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 压缩结果
     */
    @PostMapping("/api/v1/memory/compress")
    public ApiResponse<MemoryRecord> compress(@RequestBody CompressionRequest request,
                                              @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        MemoryRecord result = memoryStore.compress(request, tenantContext);
        log.info("记忆压缩完成, tenantId={}, memoryId={}",
                tenantContext.getTenantId(), result.getMemoryId());
        return ApiResponse.success(result, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }
}
