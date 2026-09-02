package com.medirag.service.rag;

import com.medirag.config.AiConfigHolder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SafetyGuard 单元测试：紧急症状检测 + 置信度评估 + 兜底决策。
 * 模型与配置均为 Mock，不依赖外部服务。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SafetyGuardTest {

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private AiConfigHolder aiConfigHolder;

    private SafetyGuard safetyGuard;

    @BeforeEach
    void setUp() {
        safetyGuard = new SafetyGuard(chatModel, aiConfigHolder);
        lenient().when(aiConfigHolder.getFloat("safety.confidence_threshold")).thenReturn(0.45f);
    }

    private RetrievedChunk chunk(String content, float score) {
        RetrievedChunk c = new RetrievedChunk();
        c.setContent(content);
        c.setScore(score);
        c.setRerankScore(0f);
        return c;
    }

    // ===================== 紧急症状检测 =====================

    @Test
    @DisplayName("isEmergency: 胸痛关键词命中")
    void isEmergencyHit() {
        assertTrue(safetyGuard.isEmergency("突然剧烈胸痛应该怎么办"));
    }

    @Test
    @DisplayName("isEmergency: 卒中/中风关键词命中")
    void isEmergencyStroke() {
        assertTrue(safetyGuard.isEmergency("家人疑似中风了"));
    }

    @Test
    @DisplayName("isEmergency: 普通问题不误报")
    void isEmergencyNormalQuestion() {
        assertFalse(safetyGuard.isEmergency("如何预防感冒"));
    }

    @Test
    @DisplayName("isEmergency: 空输入安全返回 false")
    void isEmergencyNullAndBlank() {
        assertFalse(safetyGuard.isEmergency(null));
        assertFalse(safetyGuard.isEmergency("   "));
    }

    // ===================== 置信度评估 =====================

    @Test
    @DisplayName("evaluateConfidence: 无检索结果返回 0")
    void confidenceEmptyChunks() {
        assertEquals(0f, safetyGuard.evaluateConfidence("高血压用药", List.of()), 1e-6);
        assertEquals(0f, safetyGuard.evaluateConfidence("高血压用药", null), 1e-6);
    }

    @Test
    @DisplayName("evaluateConfidence: 高相关内容返回高置信度")
    void confidenceHighRelevance() {
        // 内容需与查询词连续命中（模拟真实检索的直接命中片段）
        List<RetrievedChunk> chunks = List.of(
                chunk("高血压的治疗药物包括利尿剂、钙通道阻滞剂等，应在医生指导下使用。", 0.9f),
                chunk("高血压的治疗需要长期管理，血压控制目标因人而异。", 0.8f)
        );
        float confidence = safetyGuard.evaluateConfidence("高血压的治疗药物", chunks);
        assertTrue(confidence > 0.5f, "期望置信度 > 0.5，实际=" + confidence);
        assertTrue(confidence <= 1f);
    }

    @Test
    @DisplayName("evaluateConfidence: 无关内容返回低置信度")
    void confidenceIrrelevant() {
        List<RetrievedChunk> chunks = List.of(
                chunk("骨折的固定方法与康复训练要点。", 0.1f)
        );
        float confidence = safetyGuard.evaluateConfidence("妊娠期糖尿病筛查流程", chunks);
        assertTrue(confidence < 0.45f, "期望置信度 < 阈值，实际=" + confidence);
    }

    // ===================== 兜底决策 =====================

    @Test
    @DisplayName("needsFallback: 低于阈值触发兜底")
    void fallbackTriggered() {
        List<RetrievedChunk> chunks = List.of(
                chunk("骨折的固定方法与康复训练要点。", 0.05f)
        );
        assertTrue(safetyGuard.needsFallback("妊娠期糖尿病筛查流程", chunks));
    }

    @Test
    @DisplayName("needsFallback: 高相关不兜底")
    void fallbackNotTriggered() {
        List<RetrievedChunk> chunks = List.of(
                chunk("高血压的治疗药物包括利尿剂、钙通道阻滞剂等，应在医生指导下使用。", 0.9f),
                chunk("高血压的治疗需要长期管理，血压控制目标因人而异。", 0.8f)
        );
        assertFalse(safetyGuard.needsFallback("高血压的治疗药物", chunks));
    }

    // ===================== 提示文案 =====================

    @Test
    @DisplayName("紧急提示与兜底说明均包含就医引导")
    void noticesContainGuidance() {
        String emergency = safetyGuard.getEmergencyWarning();
        assertTrue(emergency.contains("120"));
        assertTrue(safetyGuard.getFallbackNotice().contains("咨询专业医生"));
    }
}
