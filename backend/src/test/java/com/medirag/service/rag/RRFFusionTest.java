package com.medirag.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * RRF 融合单元测试（AiConfigHolder 用 Mockito 打桩）。
 */
class RRFFusionTest {

    private RRFFusion newFusion(int kConstant) {
        AiConfigHolder holder = Mockito.mock(AiConfigHolder.class);
        when(holder.getInt("rag.rrf_k_constant")).thenReturn(kConstant);
        return new RRFFusion(holder);
    }

    private RetrievedChunk chunk(String id, String content) {
        RetrievedChunk c = new RetrievedChunk();
        c.setId(id);
        c.setContent(content);
        c.setScore(1.0f);
        return c;
    }

    @Test
    @DisplayName("fuse: 两路都命中同一 chunk 时应合并并标记 HYBRID")
    void testHybridMarking() {
        RRFFusion fusion = newFusion(60);
        List<RetrievedChunk> vector = List.of(chunk("1", "急性阑尾炎表现"));
        List<RetrievedChunk> keyword = List.of(chunk("1", "急性阑尾炎表现"));

        List<RetrievedChunk> fused = fusion.fuse(vector, keyword, 10);

        assertEquals(1, fused.size());
        assertEquals(RetrievedChunk.Source.HYBRID, fused.get(0).getSource());
        assertEquals(1, fused.get(0).getRrfRank());
    }

    @Test
    @DisplayName("fuse: 双路命中分数应高于单路命中（RRF 累加）")
    void testHybridScoresHigher() {
        RRFFusion fusion = newFusion(60);
        List<RetrievedChunk> vector = List.of(
                chunk("1", "shared document about diabetes"),
                chunk("2", "only in vector about asthma"));
        List<RetrievedChunk> keyword = List.of(chunk("1", "shared document about diabetes"));

        List<RetrievedChunk> fused = fusion.fuse(vector, keyword, 10);

        assertEquals("1", fused.get(0).getId());
        assertTrue(fused.size() >= 2);
    }

    @Test
    @DisplayName("fuse: topN 应限制输出数量")
    void testTopNLimit() {
        RRFFusion fusion = newFusion(60);
        List<RetrievedChunk> vector = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            vector.add(chunk("v" + i, "vector content " + i));
        }
        List<RetrievedChunk> fused = fusion.fuse(vector, List.of(), 3);
        assertEquals(3, fused.size());
    }

    @Test
    @DisplayName("fuse: 空输入返回空列表且不抛异常")
    void testEmptyInputs() {
        RRFFusion fusion = newFusion(60);
        assertTrue(fusion.fuse(List.of(), List.of(), 10).isEmpty());
    }

    @Test
    @DisplayName("fuse: 无 id 时按 content 前缀去重")
    void testContentDedup() {
        RRFFusion fusion = newFusion(60);
        RetrievedChunk a = chunk(null, "同一份文档的重复切片内容，应当被合并处理");
        RetrievedChunk b = chunk(null, "同一份文档的重复切片内容，应当被合并处理");
        List<RetrievedChunk> fused = fusion.fuse(List.of(a), List.of(b), 10);
        assertEquals(1, fused.size());
    }
}
