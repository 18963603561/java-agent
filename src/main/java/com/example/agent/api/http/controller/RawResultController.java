package com.example.agent.api.http.controller;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.runtime.raw.RawResultResolveResult;
import com.example.agent.runtime.raw.RawResultResolveService;
import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;

/**
 * 原始结果统一提取控制器。
 */
@RestController
public class RawResultController {

    private static final Logger log = LoggerFactory.getLogger(RawResultController.class);

    private final RawResultResolveService rawResultResolveService;
    private final AuthService authService;

    public RawResultController(RawResultResolveService rawResultResolveService, AuthService authService) {
        this.rawResultResolveService = rawResultResolveService;
        this.authService = authService;
    }

    /**
     * 统一解析并提取原始结果。
     *
     * @param ref 原始引用
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 统一提取结果
     */
    @GetMapping("/api/v1/raw/resolve")
    public ApiResponse<RawResultResolveResult> resolve(@RequestParam("ref") String ref,
                                                       @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                       ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        RawResultResolveResult result = rawResultResolveService.resolve(ref);
        log.info("原始结果解析接口完成, tenantId={}, userId={}, ref={}, storeType={}",
                tenantContext.getTenantId(),
                tenantContext.getUserId(),
                ref,
                result.getStoreType());
        return ApiResponse.success(result, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 下载本地文件原始结果。
     *
     * @param filename 文件名
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 文件下载响应
     */
    @GetMapping("/api/v1/raw/files/{filename:.+}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable("filename") String filename,
                                                      @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                      ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        String text = rawResultResolveService.downloadText(filename);
        if (text == null) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "RAW_REF_NOT_FOUND", "原始结果文件不存在");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        log.info("原始结果文件下载完成, tenantId={}, userId={}, filename={}, bytes={}",
                tenantContext.getTenantId(),
                tenantContext.getUserId(),
                filename,
                bytes.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    /**
     * 统一按引用下载原始结果。
     *
     * @param ref 原始引用
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 文件下载响应
     */
    @GetMapping("/api/v1/raw/download")
    public ResponseEntity<ByteArrayResource> downloadByRef(@RequestParam("ref") String ref,
                                                           @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                           ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        RawResultResolveResult result = rawResultResolveService.resolve(ref);
        if (result == null || result.getPayload() == null) {
            throw new ErrorCodeException(HttpStatus.NOT_FOUND, "RAW_REF_NOT_FOUND", "原始结果不存在");
        }
        byte[] bytes = result.getPayload().getBytes(StandardCharsets.UTF_8);
        String filename = "raw-result.txt";
        if (result.getResolvedRefId() != null && result.getResolvedRefId().contains(":")) {
            String normalized = result.getResolvedRefId().replace(':', '_').replace('/', '_');
            filename = normalized + ".txt";
        }
        log.info("原始结果统一下载完成, tenantId={}, userId={}, ref={}, storeType={}, bytes={}",
                tenantContext.getTenantId(),
                tenantContext.getUserId(),
                ref,
                result.getStoreType(),
                bytes.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }
}
