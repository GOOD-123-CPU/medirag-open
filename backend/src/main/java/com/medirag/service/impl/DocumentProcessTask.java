package com.medirag.service.impl;

import com.medirag.entity.MedKnowledgeBase;
import com.medirag.mapper.MedKnowledgeBaseMapper;
import com.medirag.service.knowledge.DocumentExtractor;
import com.medirag.service.knowledge.DocumentExtractor.PageContent;
import com.medirag.service.knowledge.MilvusService;
import com.medirag.service.knowledge.TextChunker;
import com.medirag.service.rag.PseudoEmbeddingUtil;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档异步处理任务（独立组件，避免 @Async 自调用导致 AOP 失效）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessTask {

    private final MedKnowledgeBaseMapper knowledgeBaseMapper;
    private final MilvusService milvusService;
    private final DocumentExtractor documentExtractor;
    private final TextChunker textChunker;
    private final OpenAiEmbeddingModel embeddingModel;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    // text-embedding-v3 单次建议批量上限 10
    private static final int EMBED_BATCH_SIZE = 10;

    /**
     * Embedding 失败时是否回退到伪向量。
     * 伪向量（字符 n-gram 哈希）质量远低于真实语义向量，会显著影响检索效果，
     * 因此默认关闭：失败即让文档进入 failed 状态，由管理员排查后重试。
     * 仅在离线演示/联调场景下建议临时开启（ALLOW_PSEUDO_EMBEDDING=true）。
     */
    @Value("${embedding.allow-pseudo-fallback:false}")
    private boolean allowPseudoFallback;

    @Async("docProcessExecutor")
    public void process(Long knowledgeBaseId) {
        MedKnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            log.error("文档处理启动失败：找不到知识库记录，id={}", knowledgeBaseId);
            return;
        }

        try {
            kb.setStatus("processing");
            kb.setErrorMsg(null);
            knowledgeBaseMapper.updateById(kb);

            Path filePath = Paths.get(uploadPath, kb.getFileUrl());
            if (!Files.exists(filePath)) {
                throw new RuntimeException("本地文件不存在: " + filePath.toAbsolutePath());
            }

            List<TextChunker.TextChunk> chunks;
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                chunks = buildChunks(inputStream, kb);
            }

            if (chunks.isEmpty()) {
                throw new RuntimeException("文档切块结果为空，请检查文件内容");
            }

            List<List<Float>> embeddings = batchEmbed(chunks);
            milvusService.insertVectors(chunks, embeddings);

            kb.setChunkCount(chunks.size());
            kb.setStatus("ready");
            kb.setErrorMsg(null);
            knowledgeBaseMapper.updateById(kb);
            log.info("文档处理完成: id={}, chunks={}", knowledgeBaseId, chunks.size());
        } catch (Exception e) {
            log.error("文档处理失败: id={}", knowledgeBaseId, e);
            kb.setStatus("failed");
            kb.setErrorMsg(e.getMessage());
            knowledgeBaseMapper.updateById(kb);
        }
    }

    private List<List<Float>> batchEmbed(List<TextChunker.TextChunk> chunks) {
        List<List<Float>> allEmbeddings = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i += EMBED_BATCH_SIZE) {
            int end = Math.min(i + EMBED_BATCH_SIZE, chunks.size());
            List<TextChunker.TextChunk> batch = chunks.subList(i, end);

            try {
                List<dev.langchain4j.data.segment.TextSegment> segments = batch.stream()
                        .map(c -> dev.langchain4j.data.segment.TextSegment.from(c.getContent()))
                        .toList();
                var response = embeddingModel.embedAll(segments);
                response.content().forEach(e -> allEmbeddings.add(e.vectorAsList()));
            } catch (Exception e) {
                if (allowPseudoFallback) {
                    log.warn("Embedding 批次失败，已启用伪向量回退（embedding.allow-pseudo-fallback=true，检索质量会下降）: {}", e.getMessage());
                    for (TextChunker.TextChunk chunk : batch) {
                        allEmbeddings.add(PseudoEmbeddingUtil.embed(chunk.getContent(), 1024));
                    }
                } else {
                    // 直接失败：让文档进入 failed 状态并携带明确错误信息，由用户排查后重试
                    throw new RuntimeException("Embedding 接口调用失败（第 " + i + "-" + end + " 批），已中止处理: " + e.getMessage(), e);
                }
            }

            log.debug("向量化进度: {}/{}", end, chunks.size());
        }

        return allEmbeddings;
    }

    private List<TextChunker.TextChunk> buildChunks(InputStream inputStream, MedKnowledgeBase kb) {
        String fileType = kb.getFileType() == null ? "" : kb.getFileType().toLowerCase();
        if ("pdf".equals(fileType)) {
            List<PageContent> pages = documentExtractor.extractPdf(inputStream);
            if (pages.isEmpty()) {
                throw new RuntimeException("PDF 文档内容为空，请检查文件");
            }
            return textChunker.chunkPdfPages(pages, kb.getId(), kb.getCategory());
        }

        String text = documentExtractor.extract(inputStream, kb.getFileType());
        if (StringUtils.isBlank(text)) {
            throw new RuntimeException("文档内容为空，请检查文件");
        }
        return textChunker.chunk(text, kb.getId(), kb.getCategory());
    }
}
