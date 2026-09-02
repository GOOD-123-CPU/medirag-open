package com.medirag.config;

import com.medirag.controller.SpeechWebSocketServer;
import com.medirag.common.utils.JwtUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.concurrent.TimeUnit;

/**
 * WebSocket 配置 - 启用 JSR-356 @ServerEndpoint 支持，并注入静态依赖
 */
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final JwtUtils jwtUtils;

    @Value("${llm.api-key}")
    private String apiKey;

    /**
     * 注册所有 @ServerEndpoint 端点
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * 将 Spring Bean 注入到 @ServerEndpoint（容器创建，非 Spring 管理，用静态字段）
     */
    @PostConstruct
    public void initSpeechWebSocket() {
        SpeechWebSocketServer.jwtUtils = jwtUtils;
        SpeechWebSocketServer.apiKey = apiKey;
        SpeechWebSocketServer.okHttpClient = new OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.MINUTES)
                .connectTimeout(15, TimeUnit.SECONDS)
                .build();
    }
}
