package com.medirag.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * KeywordRetriever BM25 风格打分单元测试（反射调用私有方法，不起 Spring 容器）。
 */
class KeywordRetrieverScoreTest {

    private float score(String content, List<String> keywords) throws Exception {
        KeywordRetriever retriever = new KeywordRetriever(
                Mockito.mock(io.milvus.client.MilvusServiceClient.class));
        Method m = KeywordRetriever.class.getDeclaredMethod("calculateBm25Score", String.class, List.class);
        m.setAccessible(true);
        return (float) m.invoke(retriever, content, keywords);
    }

    @Test
    @DisplayName("命中所有关键词的文档得分应高于只命中一个的文档")
    void testCoverageDominance() throws Exception {
        String full = "急性阑尾炎的典型表现是转移性右下腹痛，伴发热恶心";
        String partial = "急性阑尾炎需要与多种急腹症鉴别";
        float sFull = score(full, List.of("阑尾炎", "右下腹痛"));
        float sPartial = score(partial, List.of("阑尾炎", "右下腹痛"));
        assertTrue(sFull > sPartial);
    }

    @Test
    @DisplayName("零命中返回 0 分")
    void testZeroHit() throws Exception {
        assertEquals(0f, score(" completely unrelated text ", List.of("阑尾炎")), 0.0001f);
    }

    @Test
    @DisplayName("空内容或空关键词返回 0 分")
    void testEmptyInputs() throws Exception {
        assertEquals(0f, score("", List.of("阑尾炎")), 0.0001f);
        assertEquals(0f, score("急性阑尾炎", List.of()), 0.0001f);
    }

    @Test
    @DisplayName("词频重复应带来饱和收益（tf 增长收益递减但不为负）")
    void testTfSaturation() throws Exception {
        String once = "文档提到阑尾炎一次";
        String twice = "文档提到阑尾炎，阑尾炎 again";
        float sOnce = score(once, List.of("阑尾炎"));
        float sTwice = score(twice, List.of("阑尾炎"));
        assertTrue(sTwice >= sOnce);
    }

    @Test
    @DisplayName("分数不超过 1")
    void testBounded() throws Exception {
        String repeated = "阑尾炎".repeat(200);
        assertTrue(score(repeated, List.of("阑尾炎")) <= 1f);
    }
}
