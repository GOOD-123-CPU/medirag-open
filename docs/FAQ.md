# 常见问题 / FAQ

## 部署问题

### Q: `docker compose up -d` 后 Milvus 一直重启？
Milvus 是内存大户（standalone 模式建议 4GB+）。检查 `docker logs medirag-milvus`：
- 内存不足 → 调大 Docker 内存限额，或改用 Milvus Lite / 换 Qdrant（见 `ARCHITECTURE.md` 扩展指南）
- 首次启动 etcd 未就绪 → 等待 1-2 分钟自动恢复

### Q: 前端能打开但登录报 `Network Error`？
1. 确认后端健康：`curl http://localhost:8080/actuator/health` 应返回 `{"status":"UP"}`
2. 检查 `.env` 的 `JWT_SECRET` 是否 ≥ 32 字节（后端启动时会校验并拒绝弱密钥）
3. 非 Docker 本地开发时，前端 5173 端口已配置 `/api` 代理到 8080，无需额外跨域配置

### Q: 首次启动后如何登录？
空数据库时后端自动创建演示账号（见 README 表格）。若数据库非空且无账号，请用管理员账号或手动执行 `sql/` 初始化脚本。

## RAG 效果问题

### Q: 回答总是触发"证据不足"兜底？
- 检查知识库是否已导入且状态为"就绪"（知识库管理页）
- 置信度阈值可在 **AI 配置中心** 热调整（`safety.confidence_threshold`，默认见 `.env.example`）
- 关键词召回对中文长查询敏感，确保问题包含核心医学词

### Q: 重排序微服务需要启动吗？
不必须。未启动时系统降级使用 RRF 原始排名，效果略降但可用。追求检索精度建议 `docker compose --profile full up -d`（首次下载约 2GB 模型）。

### Q: 关键词检索为什么是"BM25 风格"而不是标准 BM25？
本模块是单机内存态打分，不维护全局文档频率（DF）。保留了 BM25 的词频饱和（k1=1.2）与文档长度归一（b=0.75）特性，全局加权由 RRF 融合与 Cross-Encoder 重排补偿。详细推导见 [rag-pipeline.md](rag-pipeline.md)。

## 开发问题

### Q: `mvn test` 报 forked VM crash？
多见于 Windows + 中文用户名路径下 JaCoCo agent 的编码问题。规避：
```bash
mvn test -Djacoco.skip=true          # 跳过覆盖率
mvn verify -Djacoco.destFile=D:\tmp\jacoco.exec   # 覆盖率文件写到 ASCII 路径
```

### Q: 前端构建报 element-plus / echarts chunk 大小 warning？
本项目已做拆分：`echarts` 按需引入且仅数据大屏加载，`marked`/`highlight.js` 仅问答页加载，均随路由懒加载。`element-plus` 为全站公共 chunk（~1MB，gzip 后 ~322KB），如需进一步优化可引入 `unplugin-vue-components` 按需注册组件。

### Q: 如何添加新的 RAG 后处理步骤？
在 `RRFFusion` 与 `CrossEncoderReranker` 之间插入新组件并在 `RagPipeline` 编排即可。每个环节都是无状态 Spring Bean，可独立单元测试。

## 安全与合规

### Q: 医疗数据放进去安全吗？
- 示例数据为**虚构演示数据**或 PubMed **元数据**（含 PMID/DOI 溯源），不含患者信息
- 生产环境请自建 Milvus/MySQL/MinIO 并做网络隔离，`.env` 中所有密钥务必更换
- 系统内置紧急症状检测与置信度兜底，但**不能替代专业医疗设备与医生**——这是产品定位而非缺陷

### Q: 想加一个新 LLM 供应商？
任何 OpenAI 兼容 API（DashScope / DeepSeek / Moonshot / vLLM 本地部署等）只需改 `.env`。非 OpenAI 协议的供应商，在 `AiConfigHolder` 中新增模型构造分支即可。

---

没有找到答案？[提一个 Issue](https://github.com/GOOD-123-CPU/medirag/issues/new/choose)。
