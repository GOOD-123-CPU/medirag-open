# 修改日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.2.0] - 2026-09-02

### 新增
- **架构文档**：`docs/ARCHITECTURE.md`（模块职责、关键设计决策、扩展指南）、`docs/FAQ.md`（部署/效果/开发/合规四类排查）、`docs/ROADMAP.md`（路线图 + 诚实限制清单）
- **测试覆盖**：新增 `SafetyGuardTest`（10 用例：紧急检测/置信度/兜底决策）与 `RedisRateLimiterTest`（5 用例：窗口边界/fail-open），共 37 个单元测试
- **JaCoCo 覆盖率**：`mvn verify` 自动生成覆盖率报告，CI 上传 artifact
- **CI 强化**：gitleaks 官方 action 全历史密钥扫描（替换自写 grep）、PR 依赖审查（高危 CVE 拦截）、构建产物上传
- **README 文档导航表**：六份核心文档一站式索引

### 修复
- **存储型 XSS**（重要）：LLM 输出经 `marked` 解析后未消毒直接 `v-html`，现强制经 DOMPurify 白名单消毒（禁用 `style/iframe/form` 与内联事件属性）
- **前端 bundle 体积**：ECharts 改为按需引入（`echarts/core` + 按图表类型注册），`marked`/`highlight.js` 随问答路由懒加载，登录页与问答页首屏不再加载图表库；数据大屏 chunk 从全局加载拆至路由级（554KB，仅管理员访问时下载）
- 修复 `DashboardView` 从全量 ECharts 迁移后的类型引用（`echarts.ECharts` → `echarts/core` 的 `EChartsType`）

## [1.1.0] - 2026-09-02

### 新增
- **真 BM25 风格打分**：关键词检索引入词频饱和（k1=1.2）与文档长度归一化（b=0.75），排序质量显著提升
- **接口限流**：SSE 问答接口按用户限流（10 次/分钟，Redis 滑动计数），保护 LLM 配额
- **全链路 TraceId**：每请求注入 traceId（响应头 `X-Trace-Id`），日志 pattern 与错误响应均携带，排障效率大幅提升
- **OpenAPI 文档**：集成 SpringDoc，Swagger UI 开箱即用（`/swagger-ui/index.html`），生产可关闭
- **演示账号自动初始化**：空库部署时自动创建 admin/doctor1/user1 三个演示账号
- **GitHub 社区模板**：Issue 模板（Bug/Feature）、PR 模板
- **CHANGELOG**：本文件

### 修复
- **@Async 线程池隐患**：文档入库异步任务从默认每次新建线程改为有界专用线程池 `docProcessExecutor`
- **SSE 线程池**：问答执行器从无界 `newCachedThreadPool` 改为固定 16 线程
- 清理 Vue 脚手架遗留演示组件（HelloWorld/TheWelcome 等）

### 安全
- Swagger/OpenAPI 路径纳入安全白名单但可通过配置一键关闭（生产建议关闭）

## [1.0.0] - 2026-09-02

### 首个开源版本
- 标准 monorepo 结构：backend / frontend / reranker-service / sql / docs / evaluation / sample-data
- 完整 RAG 链路：多查询召回 → 向量+关键词双路 → RRF 融合 → 重排序（远程 API 或本地降级）→ 流式生成
- 安全加固：环境变量注入密钥、JWT 强度校验、CORS 可配置、Actuator 最小暴露、伪向量回退默认关闭
- 演示数据：纯虚构演示账号与 AI 配置默认值，无任何真实个人数据
- CI：后端构建测试 + 前端类型检查构建 + 密钥扫描
- 文档：中英双语 README、RAG 技术说明、部署指南、API 概览
