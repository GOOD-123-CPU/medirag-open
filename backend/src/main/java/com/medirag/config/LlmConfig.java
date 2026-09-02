package com.medirag.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * LLM 模型配置（兼容 OpenAI 接口格式，可接入 DeepSeek/通义千问/本地 Ollama）
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.embedding-model}")
    private String embeddingModel;

    @Value("${llm.timeout}")
    private int timeout;

    /**
     * 普通对话模型（用于 Query 改写、安全检查）
     */
    @Bean
    @Lazy
    public OpenAiChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(timeout))
                .maxRetries(2)
                .build();
    }

    /**
     * 流式对话模型（用于 SSE 流式输出）
     */
    @Bean
    @Lazy
    public OpenAiStreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(timeout * 2))
                .build();
    }

    /**
     * Embedding 模型（向量化）
     * text-embedding-v3 默认输出 1024 维，与 Milvus Collection 一致
     */
    @Bean
    @Lazy
    public OpenAiEmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(embeddingModel)
                .dimensions(1024)
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
