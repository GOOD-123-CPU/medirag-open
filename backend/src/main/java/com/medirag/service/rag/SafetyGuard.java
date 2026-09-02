package com.medirag.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.medirag.config.AiConfigHolder;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SafetyGuard {

    private final OpenAiChatModel chatModel;
    private final AiConfigHolder aiConfigHolder;

    private static final Set<String> EMERGENCY_KEYWORDS = Set.of(
            "胸痛", "心梗", "心肌梗死", "卒中", "中风", "脑梗", "呼吸困难", "窒息",
            "大出血", "昏迷", "休克", "高热不退", "抽搐", "急性腹痛", "呕血", "便血",
            "过敏性休克", "急性心衰", "主动脉夹层"
    );

    public boolean isEmergency(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lowerQuery = query.toLowerCase();
        for (String keyword : EMERGENCY_KEYWORDS) {
            if (lowerQuery.contains(keyword)) {
                log.warn("Detected emergency keyword: {}", keyword);
                return true;
            }
        }
        return false;
    }

    public float evaluateConfidence(String query, List<RetrievedChunk> topChunks) {
        if (topChunks == null || topChunks.isEmpty()) {
            return 0f;
        }

        List<String> terms = QueryTermHelper.extractTerms(query);
        float topSemantic = QueryTermHelper.normalizeScore(
                topChunks.get(0).getRerankScore() > 0 ? topChunks.get(0).getRerankScore() : topChunks.get(0).getScore()
        );
        float topLexical = QueryTermHelper.lexicalCoverage(topChunks.get(0).getContent(), terms);

        int sampleSize = Math.min(3, topChunks.size());
        float avgLexical = 0f;
        float avgSemantic = 0f;
        for (int i = 0; i < sampleSize; i++) {
            RetrievedChunk chunk = topChunks.get(i);
            avgLexical += QueryTermHelper.lexicalCoverage(chunk.getContent(), terms);
            avgSemantic += QueryTermHelper.normalizeScore(chunk.getRerankScore() > 0 ? chunk.getRerankScore() : chunk.getScore());
        }
        avgLexical /= sampleSize;
        avgSemantic /= sampleSize;

        float confidence = Math.min(1f, topLexical * 0.50f + topSemantic * 0.25f + avgLexical * 0.15f + avgSemantic * 0.10f);
        if (QueryTermHelper.hasStrongGrounding(query, topChunks)) {
            confidence = Math.max(confidence, 0.62f);
        }
        return confidence;
    }

    public boolean needsFallback(String query, List<RetrievedChunk> topChunks) {
        if (QueryTermHelper.hasStrongGrounding(query, topChunks)) {
            return false;
        }
        float threshold = aiConfigHolder.getFloat("safety.confidence_threshold");
        return evaluateConfidence(query, topChunks) < threshold;
    }

    public String getEmergencyWarning() {
        return "\n\n---\n" +
                "⚠️ **紧急提示**：您描述的症状可能需要立即就医。\n" +
                "**请尽快拨打 120** 或前往最近急诊科。";
    }

    public String getFallbackNotice() {
        return "\n\n---\n" +
                "ℹ️ **说明**：当前知识库未检索到足够可靠的相关文献，以上回答仅基于通用医学知识供参考。\n" +
                "**建议尽快咨询专业医生**，获取个体化评估。";
    }

    public SafetyCheckResult evaluateAnswer(String answer) {
        try {
            String template = loadTemplate("safety_check");
            String prompt = template.replace("{{answer}}", answer);
            String response = chatModel.generate(UserMessage.from(prompt)).content().text().trim();
            response = response.replaceAll("```json|```", "").trim();
            JSONObject result = JSON.parseObject(response);

            return new SafetyCheckResult(
                    result.getBooleanValue("hasSafetyRisk"),
                    result.getBooleanValue("isEmergency"),
                    result.getFloatValue("confidence"),
                    result.getString("reason"),
                    result.getString("suggestion")
            );
        } catch (Exception e) {
            log.warn("Safety self-check failed: {}", e.getMessage());
            return new SafetyCheckResult(false, false, 0.8f, null, null);
        }
    }

    private String loadTemplate(String name) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{{answer}}";
        }
    }

    public record SafetyCheckResult(
            boolean hasSafetyRisk,
            boolean isEmergency,
            float confidence,
            String reason,
            String suggestion
    ) {
    }
}
