package com.medirag.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询词法工具单元测试。
 */
class QueryTermHelperTest {

    @Test
    @DisplayName("extractTerms: 中文问题应抽取出核心医学词与子词")
    void testExtractChineseTerms() {
        List<String> terms = QueryTermHelper.extractTerms("请问感冒发烧应该吃什么药？");
        assertFalse(terms.isEmpty());
        assertTrue(terms.stream().anyMatch(t -> t.contains("感冒") || t.contains("发烧") || t.contains("药")));
    }

    @Test
    @DisplayName("extractTerms: 停用词与纯数字应被过滤")
    void testStopWordsAndDigits() {
        List<String> terms = QueryTermHelper.extractTerms("什么 怎么 12345");
        assertTrue(terms.isEmpty() || terms.stream().noneMatch(t -> t.matches("\\d+")));
    }

    @Test
    @DisplayName("extractTerms: 英文药物名与剂量单位应被保留")
    void testEnglishAndDosage() {
        List<String> terms = QueryTermHelper.extractTerms("metformin 500mg 用法");
        assertTrue(terms.stream().anyMatch(t -> t.contains("metformin")));
        assertTrue(terms.stream().anyMatch(t -> t.contains("500mg")));
    }

    @Test
    @DisplayName("extractTerms: 空输入返回空列表")
    void testBlankInput() {
        assertTrue(QueryTermHelper.extractTerms(null).isEmpty());
        assertTrue(QueryTermHelper.extractTerms("   ").isEmpty());
    }

    @Test
    @DisplayName("lexicalCoverage: 完全包含时覆盖率为 1")
    void testFullCoverage() {
        float coverage = QueryTermHelper.lexicalCoverage(
                "急性阑尾炎的典型表现为转移性右下腹痛",
                List.of("阑尾炎", "右下腹痛"));
        assertEquals(1f, coverage, 0.01f);
    }

    @Test
    @DisplayName("lexicalCoverage: 无匹配时覆盖率为 0")
    void testZeroCoverage() {
        float coverage = QueryTermHelper.lexicalCoverage(
                "急性阑尾炎的典型表现为转移性右下腹痛",
                List.of("青光眼", "角膜"));
        assertEquals(0f, coverage, 0.01f);
    }

    @Test
    @DisplayName("normalizeScore: 分数应映射到 (0,1) 且单调")
    void testNormalizeScore() {
        float low = QueryTermHelper.normalizeScore(0.1f);
        float high = QueryTermHelper.normalizeScore(0.9f);
        assertTrue(low > 0f && low < 1f);
        assertTrue(high > low);
        assertEquals(0f, QueryTermHelper.normalizeScore(0f), 0.001f);
    }

    @Test
    @DisplayName("hasStrongGrounding: 空候选列表应返回 false")
    void testGroundingEmptyChunks() {
        assertFalse(QueryTermHelper.hasStrongGrounding("感冒怎么办", List.of()));
    }
}
