package com.medirag.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文本切块器单元测试（纯内存，无外部依赖）。
 */
class TextChunkerTest {

    private final com.medirag.service.knowledge.TextChunker chunker =
            new com.medirag.service.knowledge.TextChunker();

    @Test
    @DisplayName("chunk: 正常文本应产出多个切片并携带知识库ID")
    void testBasicChunking() {
        String text = "第一章 概述\n\n" +
                "急性阑尾炎是外科最常见的急腹症之一。".repeat(30) + "\n\n" +
                "第二章 治疗\n\n" +
                "腹腔镜阑尾切除术已成为标准术式。".repeat(30);
        List<com.medirag.service.knowledge.TextChunker.TextChunk> chunks =
                chunker.chunk(text, 42L, "waike");

        assertFalse(chunks.isEmpty());
        assertEquals(42L, chunks.get(0).getKnowledgeBaseId());
        assertEquals("waike", chunks.get(0).getCategory());
        chunks.forEach(c -> assertTrue(c.getContent().length() >= 20));
    }

    @Test
    @DisplayName("chunk: 中文医学标题应被识别为章节边界")
    void testChapterDetection() {
        String text = "概述\n\n" + "内容A。".repeat(100) + "\n\n治疗\n\n" + "内容B。".repeat(100);
        List<com.medirag.service.knowledge.TextChunker.TextChunk> chunks =
                chunker.chunk(text, 1L, "neike");
        assertTrue(chunks.stream().anyMatch(c -> c.getChapter() != null && c.getChapter().contains("治疗")));
    }

    @Test
    @DisplayName("chunk: 空文本返回空列表")
    void testEmptyText() {
        assertTrue(chunker.chunk("", 1L, "neike").isEmpty());
        assertTrue(chunker.chunk(null, 1L, "neike").isEmpty());
    }

    @Test
    @DisplayName("detectContentType: 含治疗关键词的切片应标记为 treatment")
    void testContentTypeDetection() {
        List<com.medirag.service.knowledge.TextChunker.TextChunk> chunks =
                chunker.chunk("本病的标准治疗方案如下。".repeat(10), 1L, "neike");
        assertEquals("treatment", chunks.get(0).getContentType());
    }
}
