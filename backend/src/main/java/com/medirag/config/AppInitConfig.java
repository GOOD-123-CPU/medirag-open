package com.medirag.config;

import com.medirag.service.knowledge.MilvusService;
import com.medirag.service.knowledge.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 应用启动初始化 + @Async 专用线程池。
 *
 * 说明：@EnableAsync 未显式配置 Executor 时，Spring Boot 会使用
 * SimpleAsyncTaskExecutor（每次执行都新建线程，不复用），文档入库
 * 并发时存在线程膨胀风险。这里显式定义有界线程池。
 */
@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AppInitConfig implements ApplicationRunner {

    private final MinioService minioService;
    private final MilvusService milvusService;

    /** 文档异步处理线程池：有界队列，拒绝时由调用线程执行，避免任务丢失。 */
    @Bean("docProcessExecutor")
    public ThreadPoolTaskExecutor docProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-process-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

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
