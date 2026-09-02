package com.medirag.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Prompt 组装器：拼接检索上下文、健康档案与对话历史。
 */
@Slf4j
@Component
public class PromptAssembler {

    public String assemble(String query, List<RetrievedChunk> chunks, String healthProfile, String history) {
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            contextBuilder.append("【参考文献")
                    .append(i + 1)
                    .append("】");

            if (chunk.getSourceName() != null) {
                contextBuilder.append("《").append(chunk.getSourceName()).append("》");
            }
            if (chunk.getChapter() != null && !chunk.getChapter().isEmpty()) {
                contextBuilder.append(" - ").append(chunk.getChapter());
            }
            if (chunk.getPageNumber() > 0) {
                contextBuilder.append(" 第").append(chunk.getPageNumber()).append("页");
            }
            contextBuilder.append("\n").append(chunk.getContent()).append("\n\n");
        }

        String template = loadTemplate("medical_qa");
        return template
                .replace("{{question}}", query)
                .replace("{{context}}", contextBuilder.toString())
                .replace("{{healthProfile}}", formatHealthProfile(healthProfile))
                .replace("{{history}}", history != null ? history : "（无历史对话）");
    }

    public String assembleFallback(String query, String history) {
        String historySection = (history != null && !history.isBlank())
                ? "## 对话历史\n" + history + "\n\n"
                : "";
        return String.format("""
                你是一位专业的医疗健康知识助手。
                %s## 用户当前问题
                %s

                当前知识库中未找到与该问题高度相关的医学文献。
                请先判断用户问题类型：
                - 如果用户在引用对话历史（如“上一个问题”“刚才”“之前”等），直接依据历史作答。
                - 如果是医疗问题，请基于通用医学常识做谨慎回答，并明确标注：
                  “⚠️ 知识库未匹配到相关文献，以下为通用医学信息，仅供参考。”
                - 必须提醒用户在出现加重或危险信号时及时线下就医。
                """, historySection, query);
    }

    private String formatHealthProfile(String healthProfileJson) {
        if (healthProfileJson == null || healthProfileJson.isBlank()) {
            return "（用户未填写健康档案）";
        }
        try {
            com.alibaba.fastjson2.JSONObject profile = com.alibaba.fastjson2.JSON.parseObject(healthProfileJson);
            StringBuilder sb = new StringBuilder();
            if (profile.containsKey("age")) {
                sb.append("年龄：").append(profile.get("age")).append("岁\n");
            }
            if (profile.containsKey("gender")) {
                sb.append("性别：").append(profile.get("gender")).append("\n");
            }
            if (profile.containsKey("allergies") && !profile.getString("allergies").isBlank()) {
                sb.append("过敏史：").append(profile.get("allergies")).append("\n");
            }
            if (profile.containsKey("conditions") && !profile.getString("conditions").isBlank()) {
                sb.append("慢病史：").append(profile.get("conditions")).append("\n");
            }
            if (profile.containsKey("medications") && !profile.getString("medications").isBlank()) {
                sb.append("用药史：").append(profile.get("medications")).append("\n");
            }
            return sb.isEmpty() ? "（用户未填写健康档案）" : sb.toString();
        } catch (Exception e) {
            return "（健康档案格式错误）";
        }
    }

    private String loadTemplate(String name) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Prompt template load failed: {}", name);
            return "{{question}}";
        }
    }
}
