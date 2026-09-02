package com.medirag.service.rag;

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorRetriever {

    private final OpenAiEmbeddingModel embeddingModel;
    private final MilvusServiceClient milvusClient;

    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private Integer milvusPort;

    @Value("${milvus.collection-name}")
    private String collectionName;

    public List<RetrievedChunk> retrieve(String query, String categoryFilter, int topK) {
        if (!isMilvusReachable()) {
            log.warn("Milvus is not reachable at {}:{}, skipping vector retrieval", milvusHost, milvusPort);
            return new ArrayList<>();
        }

        try {
            List<Float> queryVector = embed(query);
            String filter = categoryFilter != null && !categoryFilter.isEmpty()
                    ? "category == \"" + categoryFilter + "\""
                    : null;

            SearchParam.Builder builder = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(Arrays.asList("id", "content", "category", "content_type",
                            "chapter", "page_number", "knowledge_base_id"))
                    .withTopK(topK)
                    .withVectors(List.of(queryVector))
                    .withVectorFieldName("embedding")
                    .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                    .withParams("{\"ef\": 64}");

            if (filter != null) {
                builder.withExpr(filter);
            }

            R<SearchResults> response = milvusClient.search(builder.build());
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus vector search failed: {}", response.getMessage());
                return new ArrayList<>();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<RetrievedChunk> results = new ArrayList<>();

            for (SearchResultsWrapper.IDScore score : wrapper.getIDScore(0)) {
                int idx = results.size();
                RetrievedChunk chunk = new RetrievedChunk();
                String chunkId = score.getStrID();
                if (chunkId == null || chunkId.isBlank()) {
                    chunkId = String.valueOf(score.getLongID());
                }
                chunk.setId(chunkId);
                chunk.setScore((float) score.getScore());
                chunk.setContent(getFieldValue(wrapper, "content", idx));
                chunk.setCategory(getFieldValue(wrapper, "category", idx));
                chunk.setContentType(getFieldValue(wrapper, "content_type", idx));
                chunk.setChapter(getFieldValue(wrapper, "chapter", idx));
                chunk.setSource(RetrievedChunk.Source.VECTOR);

                String kbIdStr = getFieldValue(wrapper, "knowledge_base_id", idx);
                if (!kbIdStr.isEmpty()) {
                    try {
                        chunk.setKnowledgeBaseId(Long.parseLong(kbIdStr));
                    } catch (NumberFormatException ignored) {
                    }
                }

                String pageStr = getFieldValue(wrapper, "page_number", idx);
                if (!pageStr.isEmpty()) {
                    try {
                        chunk.setPageNumber(Integer.parseInt(pageStr));
                    } catch (NumberFormatException ignored) {
                    }
                }

                results.add(chunk);
            }

            log.info("Vector retrieval finished: query='{}', hits={}", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("Vector retrieval failed", e);
            return new ArrayList<>();
        }
    }

    public List<Float> embed(String text) {
        try {
            var response = embeddingModel.embed(text);
            return response.content().vectorAsList();
        } catch (Exception e) {
            log.error("Embedding failed: {}", e.getMessage());
            return PseudoEmbeddingUtil.embed(text, 1024);
        }
    }

    private String getFieldValue(SearchResultsWrapper wrapper, String fieldName, int index) {
        try {
            List<?> values = wrapper.getFieldData(fieldName, 0);
            if (values != null && index < values.size()) {
                return values.get(index) != null ? values.get(index).toString() : "";
            }
        } catch (Exception ignored) {
        }
        return "";
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
