package com.medirag.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 运行时配置持有者
 *
 * <p>持有所有 RAG/LLM 参数的内存快照（ConcurrentHashMap），
 * 以及当前活跃的 LLM 模型实例（AtomicReference）。
 *
 * <p>配置变更时由 AiConfigService 调用 updateBatch() + refreshLlmModel()，
 * 原子替换模型引用，无需重启 Spring；正在进行的 SSE 请求持有旧引用，自然完成后丢弃。
 */
@Slf4j
@Component
public class AiConfigHolder {

    /** 运行时配置快照 */
    private final ConcurrentHashMap<String, String> configMap = new ConcurrentHashMap<>();

    /** 当前活跃的流式对话模型（热替换） */
    private final AtomicReference<OpenAiStreamingChatModel> activeStreamingModel = new AtomicReference<>();

    /** 当前活跃的普通对话模型（用于 QueryRewriter / SafetyGuard） */
    private final AtomicReference<OpenAiChatModel> activeChatModel = new AtomicReference<>();

    /** 来自 application.yml，用于重建模型时提供连接信息 */
    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    // ======================== 读取方法 ========================

    public int getInt(String key) {
        String val = configMap.get(key);
        if (val == null) throw new IllegalStateException("AI config missing: " + key);
        return Integer.parseInt(val.trim());
    }

    public float getFloat(String key) {
        String val = configMap.get(key);
        if (val == null) throw new IllegalStateException("AI config missing: " + key);
        return Float.parseFloat(val.trim());
    }

    public String getString(String key) {
        String val = configMap.get(key);
        if (val == null) throw new IllegalStateException("AI config missing: " + key);
        return val.trim();
    }

    public OpenAiStreamingChatModel getActiveStreamingModel() {
        return activeStreamingModel.get();
    }

    public OpenAiChatModel getActiveChatModel() {
        return activeChatModel.get();
    }

    // ======================== 写入方法 ========================

    /** 批量更新内存快照（由 AiConfigService 在写完 DB 后调用） */
    public void updateBatch(Map<String, String> params) {
        configMap.putAll(params);
    }

    /**
     * 用当前 configMap 中的 llm.* 参数重建模型实例，原子替换。
     * 若 configMap 尚未初始化完毕则跳过（由 AiConfigInitializer 保证顺序）。
     */
    public void refreshLlmModel() {
        try {
            String model     = getString("llm.model");
            float  chatTemp  = getFloat("llm.chat_temperature");
            float  streamTemp = getFloat("llm.streaming_temperature");
            int    timeout   = getInt("llm.timeout_seconds");

            OpenAiChatModel newChatModel = OpenAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(model)
                    .temperature((double) chatTemp)
                    .timeout(Duration.ofSeconds(timeout))
                    .maxRetries(2)
                    .build();

            OpenAiStreamingChatModel newStreamingModel = OpenAiStreamingChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(model)
                    .temperature((double) streamTemp)
                    .timeout(Duration.ofSeconds((long) timeout * 2))
                    .build();

            activeChatModel.set(newChatModel);
            activeStreamingModel.set(newStreamingModel);

            log.info("[AiConfigHolder] 模型已热切换: model={} chatTemp={} streamTemp={} timeout={}s",
                    model, chatTemp, streamTemp, timeout);
        } catch (Exception e) {
            log.error("[AiConfigHolder] 模型重建失败，保持原实例不变", e);
        }
    }
}
