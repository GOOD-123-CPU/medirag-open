package com.medirag.service.rag;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 关键词检索器（Keyword / Lexical Retrieval）。
 *
 * 实现说明：基于 Milvus 标量字段 LIKE 匹配做多关键词 OR 召回，
 * 属于轻量级词法检索通道，与向量检索互补构成多路召回。
 * 注意：这并非严格的 BM25 算法（无词频/逆文档频率加权）。
 * 如需真正的 BM25，可升级 Milvus 2.5+ 的原生 BM25 函数或外接
 * Elasticsearch/OpenSearch，本类保留为默认的零依赖实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordRetriever {

    private final MilvusServiceClient milvusClient;

    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private Integer milvusPort;

    @Value("${milvus.collection-name}")
    private String collectionName;

    public List<RetrievedChunk> retrieve(String query, String categoryFilter, int topK) {
        if (!isMilvusReachable()) {
            log.warn("Milvus is not reachable at {}:{}, skipping keyword retrieval", milvusHost, milvusPort);
            return new ArrayList<>();
        }

        try {
            List<String> keywords = extractKeywords(query);
            if (keywords.isEmpty()) {
                return new ArrayList<>();
            }

            StringBuilder exprBuilder = new StringBuilder();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) {
                    exprBuilder.append(" or ");
                }
                String kw = keywords.get(i).replace("\"", "\\\"");
                exprBuilder.append("content like \"%").append(kw).append("%\"");
            }

            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                exprBuilder.insert(0, "(").append(") and category == \"").append(categoryFilter).append("\"");
            }

            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(exprBuilder.toString())
                    .withOutFields(Arrays.asList("id", "content", "category", "content_type",
                            "chapter", "page_number", "knowledge_base_id"))
                    .withLimit((long) Math.min(200, Math.max(topK * 4, topK)))
                    .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                    .build();

            R<QueryResults> response = milvusClient.query(queryParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.warn("Keyword retrieval failed: {}", response.getMessage());
                return new ArrayList<>();
            }

            List<RetrievedChunk> results = parseQueryResults(response.getData(), keywords);
            if (results.size() > topK) {
                results = new ArrayList<>(results.subList(0, topK));
            }
            log.info("Keyword retrieval finished: keywords={}, hits={}", keywords, results.size());
            return results;
        } catch (Exception e) {
            log.error("Keyword retrieval failed", e);
            return new ArrayList<>();
        }
    }

    public List<String> extractKeywords(String query) {
        List<String> terms = QueryTermHelper.extractTerms(query);
        if (!terms.isEmpty()) {
            return terms;
        }

        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = query.trim();
        List<String> fallback = new ArrayList<>();
        fallback.add(normalized);
        if (normalized.length() >= 4) {
            fallback.add(normalized.substring(0, Math.min(6, normalized.length())));
        }
        return fallback;
    }

    private List<RetrievedChunk> parseQueryResults(QueryResults results, List<String> keywords) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        try {
            QueryResultsWrapper wrapper = new QueryResultsWrapper(results);
            for (QueryResultsWrapper.RowRecord row : wrapper.getRowRecords()) {
                RetrievedChunk chunk = new RetrievedChunk();
                chunk.setId(String.valueOf(row.get("id")));

                String content = row.get("content") != null ? row.get("content").toString() : "";
                chunk.setContent(content);
                chunk.setCategory(row.get("category") != null ? row.get("category").toString() : "");
                chunk.setContentType(row.get("content_type") != null ? row.get("content_type").toString() : "");
                chunk.setChapter(row.get("chapter") != null ? row.get("chapter").toString() : "");

                if (row.get("page_number") != null) {
                    try {
                        chunk.setPageNumber(Integer.parseInt(row.get("page_number").toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }

                if (row.get("knowledge_base_id") != null) {
                    try {
                        chunk.setKnowledgeBaseId(Long.parseLong(row.get("knowledge_base_id").toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }

                chunk.setScore(calculateBm25Score(content, keywords));
                chunk.setSource(RetrievedChunk.Source.KEYWORD);
                chunks.add(chunk);
            }

            chunks.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        } catch (Exception e) {
            log.error("Keyword result parsing failed", e);
        }
        return chunks;
    }

    private float calculateBm25Score(String content, List<String> keywords) {
        float lexicalCoverage = QueryTermHelper.lexicalCoverage(content, keywords);
        if (lexicalCoverage == 0f || content == null || content.isBlank()) {
            return lexicalCoverage;
        }

        String lower = content.toLowerCase();
        int hits = 0;
        for (String keyword : keywords) {
            String term = keyword.toLowerCase();
            int index = 0;
            while ((index = lower.indexOf(term, index)) != -1) {
                hits++;
                index += Math.max(1, term.length());
            }
        }

        float density = hits / (float) Math.max(1, content.length() / 80);
        return Math.min(1f, lexicalCoverage * 0.7f + Math.min(1f, density) * 0.3f);
    }

    private boolean isMilvusReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(milvusHost, milvusPort), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
