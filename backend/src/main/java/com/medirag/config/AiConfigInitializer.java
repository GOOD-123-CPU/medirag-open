package com.medirag.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medirag.entity.SysAiConfig;
import com.medirag.mapper.SysAiConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 配置初始化器
 *
 * <p>Spring 启动完毕后执行：
 * <ol>
 *   <li>若 sys_ai_config 表为空（首次部署），插入 12 条默认配置</li>
 *   <li>将全部配置加载到 AiConfigHolder 的内存快照</li>
 *   <li>用当前配置重建 LLM 模型实例</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConfigInitializer implements ApplicationRunner {

    private final SysAiConfigMapper aiConfigMapper;
    private final AiConfigHolder aiConfigHolder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[AiConfigInitializer] 开始初始化 AI 配置...");

        // ① 首次部署时写入默认值
        long count = aiConfigMapper.selectCount(
                new LambdaQueryWrapper<SysAiConfig>().eq(SysAiConfig::getDeleted, 0));
        if (count == 0) {
            insertDefaults();
            log.info("[AiConfigInitializer] 已插入 {} 条默认配置", defaultConfigs().size());
        }

        // ② 从 DB 加载所有配置到内存
        List<SysAiConfig> all = aiConfigMapper.selectList(
                new LambdaQueryWrapper<SysAiConfig>().eq(SysAiConfig::getDeleted, 0));
        Map<String, String> map = all.stream()
                .collect(Collectors.toMap(SysAiConfig::getConfigKey, SysAiConfig::getConfigValue));
        aiConfigHolder.updateBatch(map);
        log.info("[AiConfigInitializer] 已加载 {} 条配置到内存", map.size());

        // ③ 构建初始 LLM 模型实例
        aiConfigHolder.refreshLlmModel();
        log.info("[AiConfigInitializer] AI 配置初始化完成");
    }

    // ======================== 默认配置 ========================

    private void insertDefaults() {
        for (SysAiConfig cfg : defaultConfigs()) {
            aiConfigMapper.insert(cfg);
        }
    }

    private List<SysAiConfig> defaultConfigs() {
        return Arrays.asList(
                build("rag", "rag.vector_top_k",         "24",       "integer", "向量召回 TopK",    "向量检索返回的最大候选数量",     "5",  "120"),
                build("rag", "rag.bm25_top_k",           "24",       "integer", "关键词召回 TopK",  "关键词检索返回的最大候选数量",   "5",  "120"),
                build("rag", "rag.rrf_top_n",            "36",       "integer", "RRF 融合 TopN",    "RRF 融合后保留的最大数量",       "5",  "240"),
                build("rag", "rag.rerank_top_k",         "8",        "integer", "重排序 TopK",      "Cross-Encoder 重排序后的最终数量", "1", "30"),
                build("rag", "rag.rrf_k_constant",       "60",       "integer", "RRF 常数 K",       "RRF 公式经验常数，值越大头部优势越小", "1", "200"),
                build("llm", "llm.model",                "qwen-plus","string",  "对话模型",         "qwen-turbo / qwen-plus / qwen-max", null, null),
                build("llm", "llm.streaming_temperature","0.3",      "float",   "流式对话温度",     "回答生成的随机性（0=确定性，1=随机）", "0.0", "2.0"),
                build("llm", "llm.chat_temperature",     "0.1",      "float",   "改写/检查温度",    "Query 改写和安全检查的温度参数",  "0.0", "2.0"),
                build("llm", "llm.timeout_seconds",      "60",       "integer", "请求超时(秒)",     "LLM 接口超时时间，流式加倍",      "10",  "300"),
                build("cache", "cache.freq_threshold",   "2",        "integer", "缓存频次阈值",     "同一问题被问几次后触发缓存写入",  "1",   "100"),
                build("cache", "cache.ttl_hours",        "2",        "integer", "缓存 TTL(小时)",   "缓存答案的保留时长",              "1",   "168"),
                build("safety", "safety.confidence_threshold", "0.45","float",  "置信度阈值",       "低于此值触发知识库未匹配兜底",    "0.0", "1.0")
        );
    }

    private SysAiConfig build(String group, String key, String value, String type,
                               String label, String desc, String min, String max) {
        SysAiConfig cfg = new SysAiConfig();
        cfg.setGroupName(group);
        cfg.setConfigKey(key);
        cfg.setConfigValue(value);
        cfg.setDefaultValue(value);
        cfg.setValueType(type);
        cfg.setLabel(label);
        cfg.setDescription(desc);
        cfg.setMinValue(min);
        cfg.setMaxValue(max);
        cfg.setDeleted(0);
        return cfg;
    }
}
