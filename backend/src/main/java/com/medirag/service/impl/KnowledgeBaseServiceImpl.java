package com.medirag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medirag.common.exception.BusinessException;
import com.medirag.common.result.ResultCode;
import com.medirag.entity.MedKnowledgeBase;
import com.medirag.mapper.MedKnowledgeBaseMapper;
import com.medirag.service.KnowledgeBaseService;
import com.medirag.service.knowledge.DocumentExtractor;
import com.medirag.service.knowledge.MilvusService;
import com.medirag.service.knowledge.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final MedKnowledgeBaseMapper knowledgeBaseMapper;
    private final MilvusService milvusService;
    private final DocumentExtractor documentExtractor;
    private final TextChunker textChunker;
    private final DocumentProcessTask documentProcessTask;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @Override
    @Transactional
    public Long uploadDocument(MultipartFile file, String category, String description) {
        String originalName = file.getOriginalFilename();
        String fileType = getFileExtension(originalName);
        if (!List.of("pdf", "docx", "doc", "txt").contains(fileType.toLowerCase())) {
            throw new BusinessException("不支持的文件格式，请上传 PDF/Word/TXT 文件");
        }

        String fileUrl = saveToLocal(file, originalName);

        MedKnowledgeBase kb = new MedKnowledgeBase();
        kb.setName(originalName);
        kb.setDescription(description);
        kb.setCategory(category);
        kb.setFileUrl(fileUrl);
        kb.setFileType(fileType);
        kb.setFileSize(file.getSize());
        kb.setStatus("uploading");
        kb.setChunkCount(0);
        knowledgeBaseMapper.insert(kb);

        final Long kbId = kb.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentProcessTask.process(kbId);
            }
        });

        log.info("Document uploaded: id={}, name={}", kb.getId(), originalName);
        return kb.getId();
    }

    @Override
    public Page<MedKnowledgeBase> listKnowledge(Integer current, Integer size, String category, String status) {
        Page<MedKnowledgeBase> page = new Page<>(current, size);
        LambdaQueryWrapper<MedKnowledgeBase> wrapper = new LambdaQueryWrapper<MedKnowledgeBase>()
                .eq(MedKnowledgeBase::getDeleted, 0)
                .eq(StringUtils.isNotBlank(category), MedKnowledgeBase::getCategory, category)
                .eq(StringUtils.isNotBlank(status), MedKnowledgeBase::getStatus, status)
                .orderByDesc(MedKnowledgeBase::getCreateTime);
        return knowledgeBaseMapper.selectPage(page, wrapper);
    }

    @Override
    public MedKnowledgeBase getById(Long id) {
        MedKnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null || kb.getDeleted() == 1) {
            throw new BusinessException(ResultCode.KNOWLEDGE_NOT_FOUND);
        }
        return kb;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        MedKnowledgeBase kb = getById(id);
        milvusService.deleteByKnowledgeBaseId(id);
        deleteLocalFile(kb.getFileUrl());
        knowledgeBaseMapper.deleteById(id);
        log.info("Knowledge deleted: id={}", id);
    }

    @Override
    public List<String> listCategories() {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<MedKnowledgeBase>()
                        .eq(MedKnowledgeBase::getDeleted, 0)
                        .select(MedKnowledgeBase::getCategory)
                        .groupBy(MedKnowledgeBase::getCategory)
        ).stream().map(MedKnowledgeBase::getCategory).distinct().collect(Collectors.toList());
    }

    @Override
    public void reprocess(Long id) {
        MedKnowledgeBase kb = getById(id);
        if ("uploading".equals(kb.getStatus()) || "processing".equals(kb.getStatus())) {
            throw new BusinessException("文档正在处理中，请稍后再试");
        }

        milvusService.deleteByKnowledgeBaseId(id);
        updateStatus(kb, "uploading", null);
        documentProcessTask.process(id);
    }

    private void updateStatus(MedKnowledgeBase kb, String status, String errorMsg) {
        kb.setStatus(status);
        kb.setErrorMsg(errorMsg);
        knowledgeBaseMapper.updateById(kb);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "txt";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String saveToLocal(MultipartFile file, String originalName) {
        try {
            String ext = getFileExtension(originalName);
            String filename = UUID.randomUUID() + "." + ext;
            Path dir = Paths.get(uploadPath, "knowledge");
            Files.createDirectories(dir);
            Path dest = dir.resolve(filename);
            file.transferTo(dest.toAbsolutePath().toFile());
            String relativePath = "knowledge/" + filename;
            log.info("Document saved locally: {}", dest.toAbsolutePath());
            return relativePath;
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
    }

    private void deleteLocalFile(String fileUrl) {
        try {
            Path path = Paths.get(uploadPath, fileUrl);
            Files.deleteIfExists(path);
            log.info("Local file deleted: {}", path.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to delete local file: {}", e.getMessage());
        }
    }
}
