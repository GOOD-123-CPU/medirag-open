package com.medirag.config;

import com.medirag.service.knowledge.MilvusService;
import com.medirag.service.knowledge.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Application startup initialization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppInitConfig implements ApplicationRunner {

    private final MinioService minioService;
    private final MilvusService milvusService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== MediRAG startup init ==========");

        try {
            minioService.initBucket();
        } catch (Exception e) {
            log.warn("MinIO initialization skipped: {}", e.getMessage());
        }

        try {
            milvusService.initCollection();
        } catch (Exception e) {
            log.warn("Milvus initialization skipped: {}", e.getMessage());
        }

        log.info("========== MediRAG startup init finished ==========");
    }
}
