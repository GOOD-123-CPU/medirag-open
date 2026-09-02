package com.medirag.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class CrossEncoderReranker {

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${reranker.api-url:}")
    private String rerankApiUrl;

    @Value("${reranker.model:}")
    private String rerankModel;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        if (rerankApiUrl == null || rerankApiUrl.isBlank() || rerankModel == null || rerankModel.isBlank()) {
            return fallbackRerank(query, candidates, topK);
        }

        if (candidates.size() <= topK) {
            return fallbackRerank(query, candidates, topK);
        }

        try {
            return callRemoteRerank(query, candidates, topK);
        } catch (Exception e) {
            log.warn("Remote rerank failed, fallback to lexical rerank: {}", e.getMessage());
            return fallbackRerank(query, candidates, topK);
        }
    }

    private List<RetrievedChunk> callRemoteRerank(String query, List<RetrievedChunk> candidates, int topK) {
        List<String> documents = candidates.stream()
                .map(RetrievedChunk::getContent)
                .toList();

        JSONObject input = new JSONObject();
        input.put("query", query);
        input.put("documents", documents);

        JSONObject parameters = new JSONObject();
        parameters.put("top_n", Math.min(topK, candidates.size()));
        parameters.put("return_documents", false);

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", rerankModel);
        requestBody.put("input", input);
        requestBody.put("parameters", parameters);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(rerankApiUrl, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("rerank API returned " + response.getStatusCode());
        }

        JSONObject result = JSON.parseObject(response.getBody());
        JSONArray results = result.getJSONObject("output").getJSONArray("results");
        List<String> terms = QueryTermHelper.extractTerms(query);

        List<RetrievedChunk> reranked = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            int originalIndex = item.getIntValue("index");
            float apiScore = item.getFloatValue("relevance_score");

            RetrievedChunk chunk = candidates.get(originalIndex);
            float lexicalScore = QueryTermHelper.lexicalCoverage(chunk.getContent(), terms);
            float semanticScore = QueryTermHelper.normalizeScore(apiScore);
            chunk.setRerankScore(Math.min(1f, semanticScore * 0.75f + lexicalScore * 0.25f));
            reranked.add(chunk);
        }

        reranked.sort(Comparator.comparing(RetrievedChunk::getRerankScore).reversed());
        return reranked.subList(0, Math.min(topK, reranked.size()));
    }

    private List<RetrievedChunk> fallbackRerank(String query, List<RetrievedChunk> candidates, int topK) {
        List<String> terms = QueryTermHelper.extractTerms(query);
        List<RetrievedChunk> reranked = new ArrayList<>(candidates);

        for (RetrievedChunk chunk : reranked) {
            float semanticScore = QueryTermHelper.normalizeScore(chunk.getScore());
            float lexicalScore = QueryTermHelper.lexicalCoverage(chunk.getContent(), terms);
            float sourceBonus = chunk.getSource() == RetrievedChunk.Source.KEYWORD ? 0.03f : 0f;
            chunk.setRerankScore(Math.min(1f, semanticScore * 0.45f + lexicalScore * 0.52f + sourceBonus));
        }

        reranked.sort(Comparator.comparing(RetrievedChunk::getRerankScore).reversed());
        return reranked.subList(0, Math.min(topK, reranked.size()));
    }
}
