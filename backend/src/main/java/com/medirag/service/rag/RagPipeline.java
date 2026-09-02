package com.medirag.service.rag;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medirag.config.AiConfigHolder;
import com.medirag.entity.MedConversation;
import com.medirag.entity.MedKnowledgeBase;
import com.medirag.entity.MedMessage;
import com.medirag.mapper.MedConversationMapper;
import com.medirag.mapper.MedKnowledgeBaseMapper;
import com.medirag.mapper.MedMessageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 核心流水线
 * 统一调度：Query改写 → 多路召回 → RRF融合 → Cross-Encoder重排序 → Prompt组装 → LLM流式生成 → 安全兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagPipeline {

    private final QueryRewriter queryRewriter;
    private final VectorRetriever vectorRetriever;
    private final KeywordRetriever keywordRetriever;
    private final RRFFusion rrfFusion;
    private final CrossEncoderReranker reranker;
    private final PromptAssembler promptAssembler;
    private final SafetyGuard safetyGuard;
    private final MedConversationMapper conversationMapper;
    private final MedMessageMapper messageMapper;
    private final MedKnowledgeBaseMapper knowledgeBaseMapper;
    private final RagCacheService ragCacheService;
    private final AiConfigHolder aiConfigHolder;

    /** 缓存命中时每批发送的字符数（模拟流式输出） */
    private static final int CACHE_CHUNK_SIZE = 30;

    /**
     * 完整 RAG 流水线（SSE 流式输出）
     *
     * @param userId         当前用户ID
     * @param conversationId 会话ID（null则自动创建）
     * @param question       用户问题
     * @param healthProfile  用户健康档案
     * @param emitter        SSE 发送器
     * @return 会话ID
     */
    public Long execute(Long userId, Long conversationId, String question,
                        String healthProfile, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();

        // ① 获取/创建会话
        MedConversation conversation = getOrCreateConversation(userId, conversationId, question);

        // ② 先读取历史（在保存本条消息之前，避免把当前问题混入 history）
        String history = buildConversationHistory(conversation.getId());

        // ③ 保存用户消息
        saveMessage(conversation.getId(), "user", question, null, null);

        // ④ 归一化问题，自增频次
        String normalized = ragCacheService.normalize(question);
        long frequency = ragCacheService.incrementFrequency(normalized);
        log.info("[RAG Cache] question freq={} normalized={}", frequency, normalized);

        // ⑤ 频次 >= 阈值时检查缓存（首次及低频时跳过，避免缓存穿透）
        if (frequency >= aiConfigHolder.getInt("cache.freq_threshold")) {
            RagCachedResult cached = ragCacheService.getCache(normalized);
            if (cached != null) {
                // 命中缓存 → 模拟流式输出后直接返回
                replayFromCache(conversation, cached, startTime, emitter);
                return conversation.getId();
            }
        }

        // ⑥ 未命中缓存 → 执行完整 RAG 流水线
        Map<String, Object> retrievalLog = new LinkedHashMap<>();
        retrievalLog.put("originalQuery", question);

        try {
            // ===== Step 1: 紧急症状检测 =====
            boolean isEmergency = safetyGuard.isEmergency(question);

            // ===== Step 2: Query 改写 =====
            String rewrittenQuery = queryRewriter.rewrite(question);
            retrievalLog.put("rewrittenQuery", rewrittenQuery);
            sendSseEvent(emitter, "rewrite", Map.of("original", question, "rewritten", rewrittenQuery));

            // ===== Step 3: 多路召回 =====
            int vectorTopK = Math.max(aiConfigHolder.getInt("rag.vector_top_k"), 20);
            int bm25TopK   = Math.max(aiConfigHolder.getInt("rag.bm25_top_k"), 20); // 关键词检索通道（沿用 bm25_top_k 配置键）
            int rrfTopN    = Math.max(aiConfigHolder.getInt("rag.rrf_top_n"), 30);
            int rerankTopK = Math.max(aiConfigHolder.getInt("rag.rerank_top_k"), 6);

            List<String> retrievalQueries = buildRetrievalQueries(question, rewrittenQuery);
            retrievalLog.put("retrievalQueries", retrievalQueries);

            List<RetrievedChunk> vectorResults = new ArrayList<>();
            List<RetrievedChunk> keywordResults = new ArrayList<>(); // 关键词检索路
            List<Map<String, Object>> perQueryStats = new ArrayList<>();

            int perQueryVectorTopK = Math.max(8, vectorTopK / Math.max(1, retrievalQueries.size()));
            int perQueryBm25TopK = Math.max(8, bm25TopK / Math.max(1, retrievalQueries.size()));

            for (String q : retrievalQueries) {
                List<RetrievedChunk> oneVector = vectorRetriever.retrieve(q, null, perQueryVectorTopK);
                List<RetrievedChunk> oneBm25 = keywordRetriever.retrieve(q, null, perQueryBm25TopK);
                vectorResults.addAll(oneVector);
                keywordResults.addAll(oneBm25);

                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("query", q);
                stats.put("vector", oneVector.size());
                stats.put("bm25", oneBm25.size() // 前端字段名保持兼容);
                perQueryStats.add(stats);
            }

            vectorResults = mergeUniqueChunks(vectorResults, vectorTopK * 2);
            keywordResults = mergeUniqueChunks(keywordResults, bm25TopK * 2);

            if (vectorResults.size() + keywordResults.size() < Math.max(12, rerankTopK * 2)) {
                List<RetrievedChunk> extraVector = vectorRetriever.retrieve(question, null, vectorTopK);
                List<RetrievedChunk> extraBm25 = keywordRetriever.retrieve(question, null, bm25TopK);
                vectorResults.addAll(extraVector);
                keywordResults.addAll(extraBm25);
                vectorResults = mergeUniqueChunks(vectorResults, vectorTopK * 3);
                keywordResults = mergeUniqueChunks(keywordResults, bm25TopK * 3);
                retrievalLog.put("adaptiveRetry", true);
            } else {
                retrievalLog.put("adaptiveRetry", false);
            }

            retrievalLog.put("vectorResults", vectorResults.size());
            retrievalLog.put("bm25Results", keywordResults.size()); // 前端字段名保持兼容
            retrievalLog.put("perQueryStats", perQueryStats);
            sendSseEvent(emitter, "retrieval", Map.of(
                    "vectorCount", vectorResults.size(),
                    "bm25Count", keywordResults.size()
            ));

            // ===== Step 4: RRF 融合 =====
            List<RetrievedChunk> fusedResults = rrfFusion.fuse(vectorResults, keywordResults, rrfTopN);
            retrievalLog.put("rrfCount", fusedResults.size());

            // ===== Step 5: Cross-Encoder 重排序 =====
            List<RetrievedChunk> topChunks = reranker.rerank(rewrittenQuery, fusedResults, rerankTopK);
            retrievalLog.put("rerankTop", topChunks.size());
            sendSseEvent(emitter, "rerank", Map.of("topK", topChunks.size()));

            // ===== Step 5.5: 批量补全文档名（knowledgeBaseId → sourceName）=====
            enrichSourceNames(topChunks);

            // ===== Step 6: 置信度评估 =====
            float retrievalConfidence = safetyGuard.evaluateConfidence(question, topChunks);
            boolean needsFallback = safetyGuard.needsFallback(question, topChunks);
            boolean grounded = QueryTermHelper.hasStrongGrounding(question, topChunks);
            retrievalLog.put("confidence", retrievalConfidence);
            retrievalLog.put("grounded", grounded);
            retrievalLog.put("isFallback", needsFallback);

            // ===== Step 7: 组装 Prompt =====
            String prompt = needsFallback
                    ? promptAssembler.assembleFallback(question, history)
                    : promptAssembler.assemble(question, topChunks, healthProfile, history);

            // ===== Step 8: LLM 流式生成 =====
            final StringBuilder answerBuilder = new StringBuilder();
            final List<Map<String, Object>> sources = buildSources(topChunks);
            sendSseEvent(emitter, "start", Map.of("message", "开始生成..."));

            aiConfigHolder.getActiveStreamingModel().generate(
                    UserMessage.from(prompt),
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            answerBuilder.append(token);
                            sendSseEvent(emitter, "token", Map.of("content", token));
                        }

                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            String answer = answerBuilder.toString();

                            // 追加安全提示
                            if (isEmergency) {
                                answer += safetyGuard.getEmergencyWarning();
                            }
                            if (needsFallback) {
                                answer += safetyGuard.getFallbackNotice();
                            }

                            // 保存 AI 回答
                            int elapsed = (int) (System.currentTimeMillis() - startTime);
                            saveMessage(conversation.getId(), "assistant", answer,
                                    JSON.toJSONString(sources), JSON.toJSONString(retrievalLog));

                            // 写入 Redis 缓存（高频时触发）
                            RagCachedResult cacheResult = new RagCachedResult(
                                    answer, sources, needsFallback, isEmergency,
                                    rewrittenQuery, retrievalLog);
                            ragCacheService.putCacheIfFrequent(normalized, cacheResult, frequency);

                            // 发送完成事件（含完整检索日志，供前端可视化）
                            Map<String, Object> donePayload = new LinkedHashMap<>();
                            donePayload.put("sources", sources);
                            donePayload.put("isFallback", needsFallback);
                            donePayload.put("isEmergency", isEmergency);
                            donePayload.put("responseTime", elapsed);
                            donePayload.put("conversationId", conversation.getId());
                            donePayload.put("retrievalLog", retrievalLog);
                            sendSseEvent(emitter, "done", donePayload);

                            emitter.complete();
                            updateConversation(conversation);
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("LLM 生成失败", error);
                            sendSseEvent(emitter, "error", Map.of("message", "生成失败，请重试"));
                            emitter.completeWithError(error);
                        }
                    }
            );

        } catch (Exception e) {
            log.error("RAG Pipeline 执行失败", e);
            sendSseEvent(emitter, "error", Map.of("message", "系统异常：" + e.getMessage()));
            emitter.completeWithError(e);
        }

        return conversation.getId();
    }

    /**
     * 从缓存结果模拟流式输出，避免再次执行 RAG 流水线
     */
    private void replayFromCache(MedConversation conversation, RagCachedResult cached,
                                  long startTime, SseEmitter emitter) {
        log.info("[RAG Cache] replaying cached answer for conversationId={}", conversation.getId());
        try {
            // 发送改写事件（来自缓存）
            sendSseEvent(emitter, "rewrite", Map.of(
                    "original", cached.getRetrievalLog().getOrDefault("originalQuery", ""),
                    "rewritten", cached.getRewrittenQuery() != null ? cached.getRewrittenQuery() : "",
                    "fromCache", true
            ));
            sendSseEvent(emitter, "start", Map.of("message", "开始生成（缓存）..."));

            // 分块发送 token，模拟流式效果
            String answer = cached.getAnswer();
            int len = answer.length();
            for (int i = 0; i < len; i += CACHE_CHUNK_SIZE) {
                String chunk = answer.substring(i, Math.min(i + CACHE_CHUNK_SIZE, len));
                sendSseEvent(emitter, "token", Map.of("content", chunk));
            }

            // 保存 AI 回答到数据库
            int elapsed = (int) (System.currentTimeMillis() - startTime);
            saveMessage(conversation.getId(), "assistant", answer,
                    JSON.toJSONString(cached.getSources()),
                    JSON.toJSONString(cached.getRetrievalLog()));

            // 发送完成事件
            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("sources", cached.getSources());
            donePayload.put("isFallback", cached.isFallback());
            donePayload.put("isEmergency", cached.isEmergency());
            donePayload.put("responseTime", elapsed);
            donePayload.put("conversationId", conversation.getId());
            donePayload.put("retrievalLog", cached.getRetrievalLog());
            donePayload.put("fromCache", true);
            sendSseEvent(emitter, "done", donePayload);

            emitter.complete();
            updateConversation(conversation);
        } catch (Exception e) {
            log.error("缓存回放失败", e);
            emitter.completeWithError(e);
        }
    }

    // ==================== 私有方法 ====================

    private MedConversation getOrCreateConversation(Long userId, Long conversationId, String question) {
        if (conversationId != null) {
            MedConversation existing = conversationMapper.selectById(conversationId);
            if (existing != null && existing.getUserId().equals(userId)) {
                return existing;
            }
        }
        // 创建新会话，标题取前20个字符
        MedConversation conv = new MedConversation();
        conv.setUserId(userId);
        conv.setTitle(question.length() > 20 ? question.substring(0, 20) + "..." : question);
        conv.setMessageCount(0);
        conv.setLastActive(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    private MedMessage saveMessage(Long conversationId, String role, String content,
                                    String sources, String retrievalLog) {
        MedMessage msg = new MedMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setSources(sources);
        msg.setRetrievalLog(retrievalLog);
        msg.setFeedback(0);
        msg.setIsFallback(0);
        messageMapper.insert(msg);
        return msg;
    }

    private void updateConversation(MedConversation conv) {
        conv.setMessageCount(conv.getMessageCount() + 2);
        conv.setLastActive(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    private String buildConversationHistory(Long conversationId) {
        // 取最近6条消息（3轮对话）作为上下文
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MedMessage> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MedMessage>()
                        .eq(MedMessage::getConversationId, conversationId)
                        .orderByDesc(MedMessage::getCreateTime)
                        .last("LIMIT 6");

        List<MedMessage> messages = messageMapper.selectList(wrapper);
        if (messages.isEmpty()) return "";

        // 反转为正序
        Collections.reverse(messages);

        return messages.stream()
                .map(m -> ("user".equals(m.getRole()) ? "用户" : "助手") + "：" + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 批量查询文档名，补全 chunk.sourceName
     */
    private void enrichSourceNames(List<RetrievedChunk> chunks) {
        Set<Long> kbIds = chunks.stream()
                .map(RetrievedChunk::getKnowledgeBaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (kbIds.isEmpty()) return;

        List<MedKnowledgeBase> kbList = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<MedKnowledgeBase>()
                        .in(MedKnowledgeBase::getId, kbIds)
                        .select(MedKnowledgeBase::getId, MedKnowledgeBase::getName)
        );
        Map<Long, String> nameMap = kbList.stream()
                .collect(Collectors.toMap(MedKnowledgeBase::getId, MedKnowledgeBase::getName));

        chunks.forEach(chunk -> {
            if (chunk.getKnowledgeBaseId() != null) {
                chunk.setSourceName(nameMap.getOrDefault(chunk.getKnowledgeBaseId(), "医学文献"));
            }
        });
    }

    private List<Map<String, Object>> buildSources(List<RetrievedChunk> chunks) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", i + 1);
            source.put("name", chunk.getSourceName() != null ? chunk.getSourceName() : "医学文献");
            source.put("chapter", chunk.getChapter());
            source.put("pageNumber", chunk.getPageNumber());
            source.put("content", chunk.getContent().substring(0, Math.min(100, chunk.getContent().length())) + "...");
            source.put("score", chunk.getRerankScore());
            sources.add(source);
        }
        return sources;
    }

    private void sendSseEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(JSON.toJSONString(data)));
        } catch (Exception e) {
            log.warn("SSE发送失败: {}", e.getMessage());
        }
    }

    private List<String> buildRetrievalQueries(String originalQuery, String rewrittenQuery) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (originalQuery != null && !originalQuery.isBlank()) {
            queries.add(originalQuery.trim());
        }
        if (rewrittenQuery != null && !rewrittenQuery.isBlank()) {
            queries.add(rewrittenQuery.trim());
        }

        List<String> extracted = QueryTermHelper.extractTerms(
                (rewrittenQuery == null ? "" : rewrittenQuery) + " " + (originalQuery == null ? "" : originalQuery)
        );
        for (String term : extracted) {
            if (term != null && term.length() >= 2) {
                queries.add(term);
            }
            if (queries.size() >= 6) {
                break;
            }
        }

        if (queries.isEmpty()) {
            return List.of(originalQuery == null ? "" : originalQuery);
        }
        return new ArrayList<>(queries);
    }

    private List<RetrievedChunk> mergeUniqueChunks(List<RetrievedChunk> chunks, int maxSize) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Map<String, RetrievedChunk> unique = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String key;
            if (chunk.getId() != null && !chunk.getId().isBlank()) {
                key = "id:" + chunk.getId();
            } else {
                String content = chunk.getContent() == null ? "" : chunk.getContent().trim();
                key = "content:" + content.substring(0, Math.min(180, content.length()));
            }
            RetrievedChunk existing = unique.get(key);
            if (existing == null || chunk.getScore() > existing.getScore()) {
                unique.put(key, chunk);
            }
        }

        List<RetrievedChunk> merged = new ArrayList<>(unique.values());
        merged.sort(Comparator.comparing(RetrievedChunk::getScore).reversed());
        if (merged.size() > maxSize) {
            return new ArrayList<>(merged.subList(0, maxSize));
        }
        return merged;
    }
}
