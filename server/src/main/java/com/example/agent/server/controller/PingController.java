package com.example.agent.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最小联调接口，返回运行状态。
 */
@RestController
public class PingController {

    /**
     * 返回固定响应，用于联调与连通性验证。
     *
     * @return 固定响应
     */
    @GetMapping("/api/v1/ping")
    public String ping() {
        return "ok";
    }
}
