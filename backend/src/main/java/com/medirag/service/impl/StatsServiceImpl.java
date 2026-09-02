package com.medirag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medirag.entity.MedConversation;
import com.medirag.entity.MedKnowledgeBase;
import com.medirag.entity.MedMessage;
import com.medirag.entity.SysUser;
import com.medirag.mapper.MedConversationMapper;
import com.medirag.mapper.MedKnowledgeBaseMapper;
import com.medirag.mapper.MedMessageMapper;
import com.medirag.mapper.SysUserMapper;
import com.medirag.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据统计服务实现
 * 直接基于 MySQL 聚合查询，无需额外统计表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final SysUserMapper userMapper;
    private final MedMessageMapper messageMapper;
    private final MedConversationMapper conversationMapper;
    private final MedKnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public Map<String, Object> getDashboard(String timeRange) {
        Map<String, Object> result = new LinkedHashMap<>();

        LocalDateTime startTime = getStartTime(timeRange);

        // ===== 核心指标卡 =====
        long totalUsers = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0));
        long totalConversations = conversationMapper.selectCount(
                new LambdaQueryWrapper<MedConversation>().eq(MedConversation::getDeleted, 0));
        long totalMessages = messageMapper.selectCount(
                new LambdaQueryWrapper<MedMessage>().eq(MedMessage::getRole, "user"));
        long totalKnowledge = knowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<MedKnowledgeBase>()
                        .eq(MedKnowledgeBase::getDeleted, 0)
                        .eq(MedKnowledgeBase::getStatus, "ready"));

        // 时间段内的问答数
        long periodMessages = messageMapper.selectCount(
                new LambdaQueryWrapper<MedMessage>()
                        .eq(MedMessage::getRole, "user")
                        .ge(MedMessage::getCreateTime, startTime));

        result.put("totalUsers", totalUsers);
        result.put("totalConversations", totalConversations);
        result.put("totalMessages", totalMessages);
        result.put("totalKnowledge", totalKnowledge);
        result.put("periodMessages", periodMessages);

        // ===== 近7天问答趋势 =====
        result.put("dailyTrend", buildDailyTrend());

        // ===== 知识库分类分布 =====
        result.put("categoryDistribution", buildCategoryDistribution());

        // ===== 兜底率统计 =====
        result.put("fallbackStats", buildFallbackStats(startTime));

        // ===== 用户角色分布 =====
        result.put("userRoleDistribution", buildUserRoleDistribution());

        // ===== 反馈统计 =====
        result.put("feedbackStats", buildFeedbackStats(startTime));

        // ===== 知识库状态统计 =====
        result.put("knowledgeStatusStats", buildKnowledgeStatusStats());

        // ===== 热门问题关键词（模拟数据）=====
        result.put("hotKeywords", buildHotKeywords());

        return result;
    }

    // ==================== 私有方法 ====================

    private LocalDateTime getStartTime(String timeRange) {
        return switch (timeRange) {
            case "week" -> LocalDateTime.now().minusDays(7);
            case "month" -> LocalDateTime.now().minusDays(30);
            default -> LocalDate.now().atStartOfDay(); // today
        };
    }

    /** 近14天问答量趋势 */
    private List<Map<String, Object>> buildDailyTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        DateTimeFormatter queryFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 13; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(23, 59, 59);

            long count = messageMapper.selectCount(
                    new LambdaQueryWrapper<MedMessage>()
                            .eq(MedMessage::getRole, "user")
                            .between(MedMessage::getCreateTime, start, end));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.format(fmt));
            item.put("count", count);
            trend.add(item);
        }
        return trend;
    }

    /** 知识库分类饼图 */
    private List<Map<String, Object>> buildCategoryDistribution() {
        List<MedKnowledgeBase> kbList = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<MedKnowledgeBase>()
                        .eq(MedKnowledgeBase::getDeleted, 0)
                        .eq(MedKnowledgeBase::getStatus, "ready")
                        .select(MedKnowledgeBase::getCategory, MedKnowledgeBase::getChunkCount));

        Map<String, Long> categoryMap = new LinkedHashMap<>();
        for (MedKnowledgeBase kb : kbList) {
            categoryMap.merge(kb.getCategory(), (long) kb.getChunkCount(), Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        // 分类标签映射
        Map<String, String> labelMap = Map.of(
                "neike", "内科", "waike", "外科", "erke", "儿科",
                "fuke", "妇科", "shenjingke", "神经科", "guke", "骨科",
                "pifuke", "皮肤科", "yaoxue", "药学", "jizhengke", "急诊科", "general", "综合"
        );
        categoryMap.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", labelMap.getOrDefault(k, k));
            item.put("value", v);
            result.add(item);
        });
        return result;
    }

    /** 兜底率统计 */
    private Map<String, Object> buildFallbackStats(LocalDateTime startTime) {
        long total = messageMapper.selectCount(
                new LambdaQueryWrapper<MedMessage>()
                        .eq(MedMessage::getRole, "assistant")
                        .ge(MedMessage::getCreateTime, startTime));
        long fallback = messageMapper.selectCount(
                new LambdaQueryWrapper<MedMessage>()
                        .eq(MedMessage::getRole, "assistant")
                        .eq(MedMessage::getIsFallback, 1)
                        .ge(MedMessage::getCreateTime, startTime));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("fallback", fallback);
        stats.put("normal", total - fallback);
        stats.put("fallbackRate", total > 0 ? String.format("%.1f%%", fallback * 100.0 / total) : "0%");
        return stats;
    }

    /** 用户角色分布 */
    private List<Map<String, Object>> buildUserRoleDistribution() {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDeleted, 0)
                        .select(SysUser::getRole));

        Map<String, Long> roleCount = users.stream()
                .collect(Collectors.groupingBy(SysUser::getRole, Collectors.counting()));

        Map<String, String> labelMap = Map.of("admin", "管理员", "doctor", "医护人员", "user", "普通用户");
        List<Map<String, Object>> result = new ArrayList<>();
        roleCount.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", labelMap.getOrDefault(k, k));
            item.put("value", v);
            result.add(item);
        });
        return result;
    }

    /** 反馈统计 */
    private Map<String, Object> buildFeedbackStats(LocalDateTime startTime) {
        long useful = messageMapper.selectCount(
                new LambdaQueryWrapper<MedMessage>()
                        .eq(MedMessage::getFeedback, 1)
                        .ge(MedMessage::getCreateTime, startTime));
        long useless = messageMapper.selectCount(
                new LambdaQueryWrapper<MedMessage>()
                        .eq(MedMessage::getFeedback, -1)
                        .ge(MedMessage::getCreateTime, startTime));
        long total = useful + useless;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("useful", useful);
        stats.put("useless", useless);
        stats.put("satisfactionRate", total > 0 ? String.format("%.1f%%", useful * 100.0 / total) : "N/A");
        return stats;
    }

    /** 知识库状态统计 */
    private List<Map<String, Object>> buildKnowledgeStatusStats() {
        String[] statuses = {"uploading", "processing", "ready", "failed"};
        Map<String, String> labels = Map.of(
                "uploading", "上传中", "processing", "处理中", "ready", "就绪", "failed", "失败");
        List<Map<String, Object>> result = new ArrayList<>();
        for (String status : statuses) {
            long count = knowledgeBaseMapper.selectCount(
                    new LambdaQueryWrapper<MedKnowledgeBase>()
                            .eq(MedKnowledgeBase::getDeleted, 0)
                            .eq(MedKnowledgeBase::getStatus, status));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", labels.get(status));
            item.put("value", count);
            result.add(item);
        }
        return result;
    }

    /** 热门关键词（基于用户消息内容分析，简化版）*/
    private List<Map<String, Object>> buildHotKeywords() {
        // 从最近50条用户消息中提取高频词（简化实现）
        List<MedMessage> recentMessages = messageMapper.selectList(
                new LambdaQueryWrapper<MedMessage>()
                        .eq(MedMessage::getRole, "user")
                        .orderByDesc(MedMessage::getCreateTime)
                        .last("LIMIT 100"));

        Map<String, Integer> wordCount = new LinkedHashMap<>();
        String[] medKeywords = {
                "发烧", "头痛", "咳嗽", "血压", "血糖", "糖尿病", "高血压", "用药", "剂量",
                "手术", "检查", "化验", "CT", "心脏", "肺", "肝", "肾", "骨折", "过敏", "消炎"
        };

        for (MedMessage msg : recentMessages) {
            String content = msg.getContent();
            for (String kw : medKeywords) {
                if (content.contains(kw)) {
                    wordCount.merge(kw, 1, Integer::sum);
                }
            }
        }

        // 按频度排序，取前15
        return wordCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }
}
