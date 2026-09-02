package com.medirag.service.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 医疗查询词法工具。
 */
public final class QueryTermHelper {

    private static final Set<String> STOP_WORDS = Set.of(
            "请问", "这个", "这种", "那个", "患者", "病人", "情况", "问题",
            "应该", "需要", "可以", "是不是", "是否", "怎么", "如何", "为什么", "怎样",
            "什么", "哪些", "多少", "有没有", "能不能", "会不会", "现在", "目前", "已经", "还有",
            "如果", "对于", "关于", "想问", "咨询", "一直", "总是", "就是", "然后", "同时",
            "出现", "排查", "诊断", "治疗", "处理"
    );

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[,，。！？；;、/\\\\()（）\\[\\]\\s]+");
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+\\-_/]{1,15}");
    private static final Pattern NUMBER_UNIT_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?(?:mg|g|ml|mmhg|mmol/l|μg|ug|%)");

    private QueryTermHelper() {
    }

    public static List<String> extractTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = query.trim().toLowerCase(Locale.ROOT);

        for (String segment : SPLIT_PATTERN.split(normalized)) {
            addTerm(segment, terms);
        }
        addPatternMatches(normalized, ENGLISH_TOKEN_PATTERN, terms);
        addPatternMatches(normalized, NUMBER_UNIT_PATTERN, terms);

        return new ArrayList<>(terms).subList(0, Math.min(terms.size(), 12));
    }

    public static float lexicalCoverage(String content, List<String> terms) {
        if (content == null || content.isBlank() || terms == null || terms.isEmpty()) {
            return 0f;
        }

        String normalizedContent = content.toLowerCase(Locale.ROOT);
        float totalWeight = 0f;
        float matchedWeight = 0f;

        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }

            float weight = Math.min(4f, Math.max(1.2f, term.length() / 2f));
            totalWeight += weight;
            if (containsLoosely(normalizedContent, term)) {
                matchedWeight += weight;
            }
        }

        if (totalWeight <= 0f) {
            return 0f;
        }
        return Math.min(1f, matchedWeight / totalWeight);
    }

    public static boolean hasStrongGrounding(String query, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return false;
        }

        List<String> terms = extractTerms(query);
        RetrievedChunk topChunk = chunks.get(0);
        float topCoverage = lexicalCoverage(topChunk.getContent(), terms);
        float topScore = normalizeScore(topChunk.getRerankScore() > 0 ? topChunk.getRerankScore() : topChunk.getScore());

        if (topCoverage >= 0.55f) {
            return true;
        }
        if (topCoverage >= 0.40f && topScore >= 0.18f) {
            return true;
        }

        int sampleSize = Math.min(3, chunks.size());
        float avgCoverage = 0f;
        float avgSemantic = 0f;
        for (int i = 0; i < sampleSize; i++) {
            RetrievedChunk c = chunks.get(i);
            avgCoverage += lexicalCoverage(c.getContent(), terms);
            avgSemantic += normalizeScore(c.getRerankScore() > 0 ? c.getRerankScore() : c.getScore());
        }
        avgCoverage /= sampleSize;
        avgSemantic /= sampleSize;

        if (avgCoverage >= 0.45f || (avgCoverage >= 0.35f && avgSemantic >= 0.25f)) {
            return true;
        }

        if (chunks.size() >= 2 && sameDocument(chunks.get(0), chunks.get(1))) {
            float topTwo = (
                    normalizeScore(chunks.get(0).getRerankScore() > 0 ? chunks.get(0).getRerankScore() : chunks.get(0).getScore()) +
                            normalizeScore(chunks.get(1).getRerankScore() > 0 ? chunks.get(1).getRerankScore() : chunks.get(1).getScore())
            ) / 2f;
            return topTwo >= 0.22f;
        }

        return false;
    }

    public static float normalizeScore(float rawScore) {
        if (rawScore <= 0f) {
            return 0f;
        }
        if (rawScore <= 1f) {
            return (float) Math.sqrt(rawScore);
        }
        return (float) (1d - (1d / (1d + rawScore)));
    }

    private static void addTerm(String term, Set<String> terms) {
        String cleaned = trimNoise(term);
        if (cleaned.length() < 2 || STOP_WORDS.contains(cleaned) || cleaned.chars().allMatch(Character::isDigit)) {
            return;
        }
        terms.add(cleaned);

        if (cleaned.length() > 6 && containsChinese(cleaned)) {
            addChineseSubTerms(cleaned, terms);
        }
    }

    private static void addPatternMatches(String text, Pattern pattern, Set<String> terms) {
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            addTerm(matcher.group().toLowerCase(Locale.ROOT), terms);
        }
    }

    private static boolean containsLoosely(String content, String term) {
        if (content.contains(term)) {
            return true;
        }
        if (term.length() >= 4 && containsChinese(term)) {
            String shortened = term.substring(0, term.length() - 1);
            return shortened.length() >= 2 && content.contains(shortened);
        }
        return false;
    }

    private static String trimNoise(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("^[^\\p{IsHan}A-Za-z0-9]+|[^\\p{IsHan}A-Za-z0-9]+$", "");
    }

    private static boolean containsChinese(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static void addChineseSubTerms(String term, Set<String> terms) {
        int added = 0;
        for (int window = Math.min(6, term.length()); window >= 2; window--) {
            for (int i = 0; i <= term.length() - window; i++) {
                String piece = term.substring(i, i + window);
                if (!looksMedical(piece) || STOP_WORDS.contains(piece)) {
                    continue;
                }
                if (terms.add(piece)) {
                    added++;
                }
                if (added >= 8) {
                    return;
                }
            }
        }
    }

    private static boolean looksMedical(String term) {
        String hints = "痛热炎癌瘤病症血药孕压糖晕吐咳痒疹肿栓梗眼腹胸腰肾肝心脑胃肠骨皮宫乳经产急慢";
        for (int i = 0; i < hints.length(); i++) {
            if (term.indexOf(hints.charAt(i)) >= 0) {
                return true;
            }
        }
        return term.length() <= 4;
    }

    private static boolean sameDocument(RetrievedChunk left, RetrievedChunk right) {
        if (left.getKnowledgeBaseId() != null && right.getKnowledgeBaseId() != null) {
            return left.getKnowledgeBaseId().equals(right.getKnowledgeBaseId());
        }
        return left.getSourceName() != null && left.getSourceName().equals(right.getSourceName());
    }
}
