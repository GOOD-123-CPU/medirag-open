package com.medirag.service.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TextChunker {

    private static final int TARGET_CHUNK_SIZE = 600;
    private static final int MAX_CHUNK_SIZE = 900;
    private static final int OVERLAP_SIZE = 120;
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("(?<=[。！？；.!?;\\n])");

    public List<TextChunk> chunk(String text, Long knowledgeBaseId, String category) {
        String normalizedText = normalize(text);
        String[] paragraphs = normalizedText.split("\n\n");
        List<ChunkDraft> drafts = mergeParagraphs(paragraphs);
        return buildChunks(drafts, knowledgeBaseId, category, 0);
    }

    public List<TextChunk> chunkPdfPages(List<DocumentExtractor.PageContent> pages,
                                         Long knowledgeBaseId,
                                         String category) {
        List<TextChunk> chunks = new ArrayList<>();
        for (DocumentExtractor.PageContent page : pages) {
            if (page == null || page.text() == null || page.text().isBlank()) {
                continue;
            }
            String normalizedText = normalize(page.text());
            String[] paragraphs = normalizedText.split("\n\n");
            List<ChunkDraft> drafts = mergeParagraphs(paragraphs);
            chunks.addAll(buildChunks(drafts, knowledgeBaseId, category, page.pageNumber()));
        }
        log.info("PDF text chunking completed: pages={}, chunks={}", pages.size(), chunks.size());
        return chunks;
    }

    private List<TextChunk> buildChunks(List<ChunkDraft> drafts, Long knowledgeBaseId, String category, int pageNumber) {
        List<TextChunk> chunks = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            String content = draft.content().trim();
            if (content.length() < 20) {
                continue;
            }

            TextChunk chunk = new TextChunk();
            chunk.setContent(content);
            chunk.setChunkIndex(i);
            chunk.setKnowledgeBaseId(knowledgeBaseId);
            chunk.setCategory(category);
            chunk.setContentType(detectContentType(content));
            chunk.setChapter(draft.chapter());
            chunk.setPageNumber(pageNumber);
            chunks.add(chunk);
        }
        return chunks;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace('\u3000', ' ')
                .replace("\uFFFD", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private List<ChunkDraft> mergeParagraphs(String[] paragraphs) {
        List<ChunkDraft> result = new ArrayList<>();
        String currentChapter = "";
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            String para = paragraph.trim();
            if (para.isEmpty()) {
                continue;
            }

            if (isHeading(para)) {
                flushDraft(result, buffer, currentChapter);
                currentChapter = cleanupHeading(para);
                continue;
            }

            if (para.length() > MAX_CHUNK_SIZE) {
                flushDraft(result, buffer, currentChapter);
                result.addAll(splitBySentence(para, currentChapter));
                continue;
            }

            if (buffer.isEmpty()) {
                buffer.append(para);
                continue;
            }

            if (buffer.length() + 2 + para.length() <= TARGET_CHUNK_SIZE) {
                buffer.append("\n\n").append(para);
            } else {
                flushDraft(result, buffer, currentChapter);
                buffer.append(para);
            }
        }

        flushDraft(result, buffer, currentChapter);
        return result;
    }

    private List<ChunkDraft> splitBySentence(String text, String chapter) {
        List<ChunkDraft> chunks = new ArrayList<>();
        String[] sentences = SENTENCE_SPLITTER.split(text);
        StringBuilder buffer = new StringBuilder();

        for (String sentence : sentences) {
            String current = sentence.trim();
            if (current.isEmpty()) {
                continue;
            }

            if (buffer.length() + current.length() > MAX_CHUNK_SIZE && !buffer.isEmpty()) {
                chunks.add(new ChunkDraft(chapter, buffer.toString().trim()));
                String overlap = buffer.length() > OVERLAP_SIZE
                        ? buffer.substring(buffer.length() - OVERLAP_SIZE)
                        : buffer.toString();
                buffer = new StringBuilder(overlap);
            }

            if (!buffer.isEmpty() && buffer.charAt(buffer.length() - 1) != '\n') {
                buffer.append(' ');
            }
            buffer.append(current);
        }

        if (!buffer.isEmpty()) {
            chunks.add(new ChunkDraft(chapter, buffer.toString().trim()));
        }

        return chunks;
    }

    private void flushDraft(List<ChunkDraft> result, StringBuilder buffer, String chapter) {
        if (buffer.isEmpty()) {
            return;
        }
        result.add(new ChunkDraft(chapter, buffer.toString().trim()));
        buffer.setLength(0);
    }

    private boolean isHeading(String paragraph) {
        if (paragraph.length() > 40) {
            return false;
        }
        return paragraph.matches("^第[一二三四五六七八九十百0-9]+[章节部分篇].*$")
                || paragraph.matches("^[一二三四五六七八九十]+[、.．].*$")
                || paragraph.matches("^[(（]?[0-9]{1,2}[)）][、.．]?.*$")
                || paragraph.matches("^(概述|定义|病因|症状|体征|诊断|鉴别诊断|检查|治疗|处理|用药|随访|转诊|急诊处置|患者教育).*$");
    }

    private String cleanupHeading(String heading) {
        return heading.replaceAll("\\s+", " ").trim();
    }

    private String detectContentType(String content) {
        if (content.contains("症状") || content.contains("表现") || content.contains("体征")) {
            return "symptom";
        }
        if (content.contains("治疗") || content.contains("手术") || content.contains("疗法")
                || content.contains("处置") || content.contains("干预")) {
            return "treatment";
        }
        if (content.contains("药") || content.contains("剂量") || content.contains("禁忌")
                || content.contains("不良反应") || content.contains("相互作用")) {
            return "drug";
        }
        if (content.contains("定义") || content.contains("概念") || content.contains("是指")
                || content.contains("概述")) {
            return "definition";
        }
        if (content.contains("病例") || content.contains("病史") || content.contains("主诉")) {
            return "case";
        }
        return "general";
    }

    private record ChunkDraft(String chapter, String content) {
    }

    public static class TextChunk {
        private String content;
        private int chunkIndex;
        private Long knowledgeBaseId;
        private String category;
        private String contentType;
        private int pageNumber;
        private String chapter;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public Long getKnowledgeBaseId() {
            return knowledgeBaseId;
        }

        public void setKnowledgeBaseId(Long knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        public String getChapter() {
            return chapter;
        }

        public void setChapter(String chapter) {
            this.chapter = chapter;
        }
    }
}
