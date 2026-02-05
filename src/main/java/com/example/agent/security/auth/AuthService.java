package com.example.agent.security.auth;

import org.springframework.web.server.ServerWebExchange;

/**
 * 鉴权服务接口，提供 API Key 等鉴权扩展点。
 */
public interface AuthService {

    /**
     * 鉴权并返回用户上下文。
     *
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 用户上下文
     */
    UserContext authenticate(String apiKey, ServerWebExchange exchange);
}
