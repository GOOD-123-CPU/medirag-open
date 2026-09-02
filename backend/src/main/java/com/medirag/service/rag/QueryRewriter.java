package com.medirag.service.rag;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 查询改写器：将口语化问题转为高召回检索词串。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriter {

    private final OpenAiChatModel chatModel;
    private static final int MAX_QUERY_LENGTH = 200;

    public String rewrite(String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) {
            return "";
        }
        if (originalQuery.length() <= 8) {
            return originalQuery;
        }

        try {
            String prompt = loadPromptTemplate("query_rewrite")
                    .replace("{{question}}", originalQuery);

            String rewritten = chatModel.generate(UserMessage.from(prompt))
                    .content()
                    .text()
                    .trim();

            rewritten = sanitizeRewrittenQuery(rewritten, originalQuery);
            if (rewritten.length() > MAX_QUERY_LENGTH) {
                rewritten = rewritten.substring(0, MAX_QUERY_LENGTH);
            }
            log.info("Query rewrite: [{}] -> [{}]", originalQuery, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("Query rewrite failed, fallback to original query: {}", e.getMessage());
            return originalQuery;
        }
    }

    private String loadPromptTemplate(String name) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Prompt template load failed: {}", name);
            return "{{question}}";
        }
    }

    private String sanitizeRewrittenQuery(String rewritten, String originalQuery) {
        if (rewritten == null || rewritten.isBlank()) {
            return originalQuery;
        }

        String normalized = rewritten
                .replace("```", "")
                .replace("改写后的检索词：", "")
                .replace("检索词：", "")
                .replace("\"", "")
                .trim();

        String[] lines = normalized.split("\\R");
        String firstLine = lines.length > 0 ? lines[0].trim() : normalized;
        if (firstLine.isBlank()) {
            return originalQuery;
        }
        return firstLine;
    }
}
