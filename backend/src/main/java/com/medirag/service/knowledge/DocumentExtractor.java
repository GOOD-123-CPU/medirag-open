package com.medirag.service.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档文本提取器，支持 PDF / Word / TXT。
 */
@Slf4j
@Component
public class DocumentExtractor {

    public List<PageContent> extractPdf(InputStream inputStream) {
        List<PageContent> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(inputStream))) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document).trim();
                if (!text.isEmpty()) {
                    pages.add(new PageContent(i, text));
                }
            }
            log.info("PDF 提取完成: totalPages={}, nonEmptyPages={}", totalPages, pages.size());
        } catch (IOException e) {
            log.error("PDF 提取失败", e);
            throw new RuntimeException("PDF 解析失败: " + e.getMessage());
        }
        return pages;
    }

    public String extractDocx(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            log.info("DOCX 提取完成: chars={}", text.length());
            return text;
        } catch (IOException e) {
            log.error("DOCX 提取失败", e);
            throw new RuntimeException("Word 文档解析失败: " + e.getMessage());
        }
    }

    public String extractTxt(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("TXT 提取失败", e);
            throw new RuntimeException("文本文件读取失败: " + e.getMessage());
        }
    }

    public String extract(InputStream inputStream, String fileType) {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> {
                List<PageContent> pages = extractPdf(inputStream);
                StringBuilder sb = new StringBuilder();
                for (PageContent p : pages) {
                    sb.append(p.text()).append("\n\n");
                }
                yield sb.toString();
            }
            case "docx", "doc" -> extractDocx(inputStream);
            case "txt" -> extractTxt(inputStream);
            default -> throw new RuntimeException("不支持的文件格式: " + fileType);
        };
    }

    public record PageContent(int pageNumber, String text) {
    }
}
