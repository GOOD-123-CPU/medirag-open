package com.medirag.service.knowledge;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.IndexType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusService {

    private final MilvusServiceClient milvusClient;

    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private Integer milvusPort;

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.dimension}")
    private Integer dimension;

    public void initCollection() {
        if (!isMilvusReachable()) {
            log.warn("Milvus is not reachable at {}:{}, skipping collection initialization", milvusHost, milvusPort);
            return;
        }

        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder().withCollectionName(collectionName).build());

            if (Boolean.TRUE.equals(hasCollection.getData())) {
                log.info("Milvus collection already exists: {}", collectionName);
                return;
            }

            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();

            FieldType embeddingField = FieldType.newBuilder()
                    .withName("embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .build();

            FieldType kbIdField = FieldType.newBuilder()
                    .withName("knowledge_base_id")
                    .withDataType(DataType.Int64)
                    .build();

            FieldType categoryField = FieldType.newBuilder()
                    .withName("category")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(50)
                    .build();

            FieldType contentTypeField = FieldType.newBuilder()
                    .withName("content_type")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(30)
                    .build();

            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(4096)
                    .build();

            FieldType chapterField = FieldType.newBuilder()
                    .withName("chapter")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(200)
                    .build();

            FieldType pageField = FieldType.newBuilder()
                    .withName("page_number")
                    .withDataType(DataType.Int64)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription("Medical knowledge chunks")
                    .withShardsNum(2)
                    .addFieldType(idField)
                    .addFieldType(embeddingField)
                    .addFieldType(kbIdField)
                    .addFieldType(categoryField)
                    .addFieldType(contentTypeField)
                    .addFieldType(contentField)
                    .addFieldType(chapterField)
                    .addFieldType(pageField)
                    .build();

            R<RpcStatus> createResult = milvusClient.createCollection(createParam);
            if (createResult.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Failed to create collection: " + createResult.getMessage());
            }

            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("embedding")
                    .withIndexType(IndexType.HNSW)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"M\": 16, \"efConstruction\": 64}")
                    .build();

            milvusClient.createIndex(indexParam);
            milvusClient.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collectionName).build());
            log.info("Milvus collection initialized: {}", collectionName);
        } catch (Exception e) {
            log.warn("Milvus initialization failed: {}", e.getMessage());
        }
    }

    public void insertVectors(List<TextChunker.TextChunk> chunks, List<List<Float>> embeddings) {
        if (chunks.isEmpty()) {
            return;
        }

        List<String> ids = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        List<Long> kbIds = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<String> contentTypes = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> chapters = new ArrayList<>();
        List<Long> pageNumbers = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            TextChunker.TextChunk chunk = chunks.get(i);
            ids.add(UUID.randomUUID().toString().replace("-", ""));
            vectors.add(embeddings.get(i));
            kbIds.add(chunk.getKnowledgeBaseId());
            categories.add(nullToEmpty(chunk.getCategory()));
            contentTypes.add(nullToEmpty(chunk.getContentType()));
            String content = chunk.getContent();
            contents.add(content.length() > 4000 ? content.substring(0, 4000) : content);
            chapters.add(nullToEmpty(chunk.getChapter()));
            pageNumbers.add((long) chunk.getPageNumber());
        }

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(Arrays.asList(
                        new InsertParam.Field("id", ids),
                        new InsertParam.Field("embedding", vectors),
                        new InsertParam.Field("knowledge_base_id", kbIds),
                        new InsertParam.Field("category", categories),
                        new InsertParam.Field("content_type", contentTypes),
                        new InsertParam.Field("content", contents),
                        new InsertParam.Field("chapter", chapters),
                        new InsertParam.Field("page_number", pageNumbers)
                ))
                .build();

        R<MutationResult> result = milvusClient.insert(insertParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Failed to insert vectors: " + result.getMessage());
        }
        log.info("Inserted {} vectors into Milvus", chunks.size());
    }

    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        try {
            String expr = "knowledge_base_id == " + knowledgeBaseId;
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();
            milvusClient.delete(deleteParam);
            log.info("Deleted vectors for knowledge base {}", knowledgeBaseId);
        } catch (Exception e) {
            log.warn("Failed to delete vectors: {}", e.getMessage());
        }
    }

    public List<SearchResult> search(List<Float> queryVector, String categoryFilter, int topK) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0) {
            return new ArrayList<>();
        }

        if (!isMilvusReachable()) {
            log.warn("Milvus is not reachable at {}:{}, skipping search", milvusHost, milvusPort);
            return new ArrayList<>();
        }

        try {
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

            if (categoryFilter != null && !categoryFilter.isBlank()) {
                builder.withExpr("category == \"" + categoryFilter.replace("\"", "\\\"") + "\"");
            }

            R<SearchResults> response = milvusClient.search(builder.build());
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.warn("Milvus search failed: {}", response.getMessage());
                return new ArrayList<>();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<SearchResult> results = new ArrayList<>();
            List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

            for (int i = 0; i < idScores.size(); i++) {
                SearchResultsWrapper.IDScore idScore = idScores.get(i);
                String resultId = idScore.getStrID();
                if (resultId == null || resultId.isBlank()) {
                    resultId = String.valueOf(idScore.getLongID());
                }

                results.add(new SearchResult(
                        resultId,
                        idScore.getScore(),
                        getFieldValue(wrapper, "content", i),
                        getFieldValue(wrapper, "category", i),
                        getFieldValue(wrapper, "content_type", i),
                        getFieldValue(wrapper, "chapter", i),
                        parseInt(getFieldValue(wrapper, "page_number", i)),
                        parseLong(getFieldValue(wrapper, "knowledge_base_id", i))
                ));
            }

            return results;
        } catch (Exception e) {
            log.warn("Milvus search failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String getFieldValue(SearchResultsWrapper wrapper, String fieldName, int index) {
        try {
            List<?> values = wrapper.getFieldData(fieldName, 0);
            if (values != null && index < values.size()) {
                Object value = values.get(index);
                return value == null ? "" : value.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private int parseInt(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? 0L : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private boolean isMilvusReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(milvusHost, milvusPort), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record SearchResult(
            String id,
            float score,
            String content,
            String category,
            String contentType,
            String chapter,
            int pageNumber,
            long knowledgeBaseId
    ) {}
}
