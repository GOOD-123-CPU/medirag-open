# 系统架构 / Architecture

> 本文面向希望深入理解或二次开发 MediRAG 的工程师。
> 部署拓扑见 [deployment.md](deployment.md)，RAG 链路细节见 [rag-pipeline.md](rag-pipeline.md)，API 契约见 [api.md](api.md)。

## 1. 总体架构

```
┌─────────────────────────── 前端 (Vue 3 + Vite) ───────────────────────────┐
│  ChatView(问答)  KnowledgeView(知识库)  DashboardView(ECharts 大屏)        │
│  Pinia 状态管理 · SSE 流式渲染 · DOMPurify 消毒 · Element Plus             │
└──────────────────────────────────┬────────────────────────────────────────┘
                                   │ HTTPS (Nginx 反代 /api, SSE 优化)
┌──────────────────────────────────▼────────────────────────────────────────┐
│                        后端 (SpringBoot 3 / Java 17)                       │
│                                                                            │
│  ┌────────────┐  ┌──────────────────────────────────────────────────────┐ │
│  │ Controller │  │                RAG Pipeline (核心)                   │ │
│  │ Chat       │  │  QueryRewriter → [VectorRetriever + KeywordRetriever]│ │
│  │ Knowledge  │──▶  → RRFFusion → CrossEncoderReranker → PromptAssembler│ │
│  │ AiConfig   │  │  → LLM SSE 流式生成 → SafetyGuard 兜底               │ │
│  │ Stats      │  └──────────────────────────────────────────────────────┘ │
│  │ User       │                                                            │
│  └─────┬──────┘  ┌──────────────────────────────────────────────────────┐ │
│        │         │                   横切组件                            │ │
│        │         │  TraceIdFilter(MDC) · RedisRateLimiter · Swagger     │ │
│        │         │  GlobalExceptionHandler · DemoAccountInitializer     │ │
│        │         └──────────────────────────────────────────────────────┘ │
└────────┼───────────────────────────────────────────────────────────────────┘
         │
   ┌─────┼──────────┬──────────────┬──────────────┬───────────────┐
   ▼     ▼          ▼              ▼              ▼               ▼
 MySQL   Redis     Milvus        MinIO      Reranker(可选)    LLM API
(业务库) (缓存/限流) (向量库)   (原始文档)  (bge-reranker)  (OpenAI 兼容)
```

## 2. 模块职责

### 2.1 后端包结构（`com.medirag`）

| 包 | 职责 | 关键类 |
|---|---|---|
| `service.rag` | RAG 核心链路，每一步一个组件，均可独立替换 | `RagPipeline`（编排）、`VectorRetriever`、`KeywordRetriever`（BM25 风格打分）、`RRFFusion`、`CrossEncoderReranker`、`PromptAssembler`、`SafetyGuard` |
| `service.knowledge` | 知识库入库链路 | `DocumentExtractor`（PDF/DOCX 解析）、`TextChunker`（语义切块）、`MilvusService`、`MinioService` |
| `controller` | REST/SSE/WS 接口层 | `ChatController`（SSE 流式 + 限流）、`SpeechWebSocketServer`（语音代理） |
| `config` | 安全与中间件装配 | `SecurityConfig`（JWT + 可环境化 CORS）、`OpenApiConfig`、`AppInitConfig`（线程池） |
| `common.web` | 可观测性 | `TraceIdFilter`（MDC traceId + `X-Trace-Id` 响应头） |
| `common.ratelimit` | 流量防护 | `RedisRateLimiter`（固定窗口，Redis 故障 fail-open） |
| `common.exception` | 统一错误出口 | `GlobalExceptionHandler`（错误消息携带 traceId） |
| `security` | 认证 | JWT 校验过滤器、Spring Security 配置 |

### 2.2 前端结构（`frontend/src`）

| 目录 | 职责 |
|---|---|
| `views/chat` | 问答主界面：SSE 打字机、检索日志可视化、来源引用、语音输入 |
| `views/dashboard` | 数据大屏：ECharts **按需引入**（`echarts/core` + 用到的图表模块），随路由懒加载 |
| `views/knowledge` / `admin` / `user` | 知识库管理、AI 参数热调整、用户管理 |
| `api/` | 按领域拆分的 API 客户端（axios 封装） |
| `stores/` | Pinia：用户会话状态 |

**Markdown 渲染安全**：LLM 输出经 `marked` 解析后**必须**过 `DOMPurify.sanitize` 再 `v-html`，白名单模式（`USE_PROFILES: { html: true }`）并显式禁用 `style/iframe/form` 与内联事件，防止存储型 XSS。

## 3. 关键设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 关键词检索打分 | BM25 风格局部打分（tf 饱和 k1=1.2、长度归一 b=0.75，无全局 IDF） | 单机内存态索引不维护全局 DF；全局加权由 RRF + Cross-Encoder 重排补偿。文档见 `KeywordRetriever` |
| 双路召回融合 | RRF（Reciprocal Rank Fusion） | 对异构分数量纲不敏感，k=60 经典配置，无需调参 |
| 置信度评估 | 词面覆盖率 50% + 语义分 25% + 均值项 25%，强证据下限 0.62 | 纯规则可解释、零成本，适合医疗场景"证据不足宁可不答"的兜底逻辑 |
| 异步任务 | `docProcessExecutor` 专用 ThreadPoolTaskExecutor（core 2 / max 4 / queue 50） | 规避 Spring Boot 3 默认 `SimpleAsyncTaskExecutor` 每任务新建线程的失控风险 |
| SSE 并发 | 固定 16 线程池 | 有界资源，防止突发会话耗尽线程 |
| 配置热更新 | `AiConfigHolder` 原子引用替换模型实例 | 调参不重启；进行中的 SSE 持有旧引用自然结束 |
| 限流 | Redis INCR+EXPIRE 固定窗口 | 实现简单、足够保护 LLM 下游；窗口边界 2 倍突发可接受，需要更平滑可换 Redis Cell |
| 可观测性 | TraceId 从 Filter 注入 MDC，贯穿日志与异常响应 | 免接入成本的全链路追踪，前端可回传 `X-Trace-Id` 排障 |

## 4. 数据流：一次提问的生命周期

1. **限流检查** — `rate:chat:{userId}` 固定窗口（10 次/分钟），超限返回 SSE `error` 事件
2. **缓存命中检查** — Redis 以规范化问题为 key，命中直接回放缓存答案
3. **紧急检测** — `SafetyGuard.isEmergency` 命中则叠加就医强提示
4. **Query 改写** — LLM 补全指代、抽取检索词
5. **双路召回** — Milvus COSINE Top-K + 关键词 BM25 风格打分 Top-K
6. **RRF 融合** — 按 `1/(60+rank)` 融合两路排名
7. **重排序** — bge-reranker-v2-m3（HTTP 调 Python 微服务），未启用时降级用原始分
8. **置信度评估** — 低于阈值叠加"证据不足"兜底说明而非硬编
9. **Prompt 组装** — 系统约束 + 健康档案 + 对话历史 + 引用切片
10. **流式生成** — OpenAI 兼容 SSE，逐 token 推送前端
11. **落库与缓存** — 消息/来源/检索日志入库，答案写 Redis

## 5. 测试与质量

- **单元测试 37 个**：RAG 打分/融合/分块/词法/安全兜底/限流器全部覆盖，`mvn verify` 一键执行
- **覆盖率**：JaCoCo（`backend/target/site/jacoco/index.html`），CI 上传 artifact
- **密钥防线**：gitleaks 扫描全量历史 + working tree，杜绝 `sk-` 形态 Key、私钥块入库
- **依赖审查**：PR 级 dependency-review，高危 CVE 直接拦截
- **前端质量门禁**：`vue-tsc` 严格模式类型检查 + 构建内联在 `npm run build`

## 6. 扩展指南

| 想做什么 | 动哪里 |
|---|---|
| 换向量库 | 实现 `VectorRetriever` 同款接口，替换 `MilvusService` |
| 换重排模型 | `reranker-service/main.py`（FastAPI），接口协议不变即可 |
| 换 LLM | 任何 OpenAI 兼容 API；改 `.env` 的 `LLM_BASE_URL`/`LLM_API_KEY` |
| 加检索后处理 | 在 `RRFFusion` 与 `CrossEncoderReranker` 之间插入新组件 |
| 新增 UI 页面 | `views/` 加组件 + `router/index.ts` 注册路由（自动代码分割） |
