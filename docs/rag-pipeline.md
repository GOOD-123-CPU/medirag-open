# MediRAG RAG 链路技术说明

> 本文档面向希望理解或二次开发 RAG 链路的开发者。

## 1. 端到端流程

```
用户问题
  ↓
① 紧急症状检测（SafetyGuard，本地关键词，命中则追加就医提示）
  ↓
② Query 改写（LLM，prompts/query_rewrite.txt）
  ↓
③ 多查询召回（原问题 + 改写问题 + 词法抽取关键词，最多 6 路）
  ├── 向量检索 VectorRetriever（Milvus COSINE，embedding: text-embedding-v3 1024维）
  └── 关键词检索 KeywordRetriever（Milvus 标量 LIKE 匹配，零依赖实现）
  ↓
④ 自适应补捞（两路合计不足 rerankTopK*2 时，用原始问题补召一轮）
  ↓
⑤ RRF 融合（score = Σ 1/(k + rank)，k 默认 60，可热配置）
  ↓
⑥ 重排序 CrossEncoderReranker
  ├── 优先：远程 rerank API（DashScope 兼容格式，见 reranker-service/）
  └── 降级：词法+语义混合打分（semantic*0.45 + lexical*0.52 + 来源微调）
  ↓
⑦ 置信度评估（词法覆盖率 50% + 语义分 25% + 均值项 25%）
  ├── 低于阈值 → 兜底 Prompt（不引用知识库，明确告知证据不足）
  └── 强接地判定（hasStrongGrounding）通过则跳过兜底
  ↓
⑧ Prompt 组装（医学 QA 模板 + 健康档案 + 3 轮对话历史）
  ↓
⑨ LLM SSE 流式生成（打字机效果）
  ↓
⑩ 落库 + 高频缓存（Redis，问满 N 次后缓存答案并模拟流式回放）
```

## 2. 关键设计取舍（诚实说明）

### 2.1 关键词检索采用 BM25 风格局部打分
`KeywordRetriever` 用 Milvus 标量字段 `LIKE` 匹配做多关键词 OR 召回，
召回后按 BM25 风格公式重排序：词频饱和（k1=1.2）+ 文档长度归一化（b=0.75），
查询词覆盖率为主要权重。因缺少全库文档频率，未做全局 IDF 加权（局部近似）；
排序质量在单查询内已足够稳定，全局加权由 RRF 融合与 Cross-Encoder 补足。
如需严格的全库 BM25，升级路径：
- Milvus 2.5+ 原生 BM25 函数（推荐）
- 外接 Elasticsearch / OpenSearch

### 2.2 伪向量回退默认关闭
Embedding 调用失败时，旧版本会静默生成字符 n-gram 伪向量入库，
导致检索质量劣化且难以察觉。现改为：
- 默认：失败即让文档进入 `failed` 状态，附带明确错误信息
- 演示模式：`EMBEDDING_ALLOW_PSEUDO_FALLBACK=true` 时才启用伪向量

### 2.3 重排序双模式
- 配置 `RERANKER_API_URL` + `RERANKER_MODEL` 后走远程 API
  （仓库附带 Python 微服务 `reranker-service/`，兼容 DashScope rerank 格式）
- 未配置或调用失败时自动降级为词法混合打分，保证链路永远可用

### 2.4 缓存策略
同一归一化问题被提问 ≥ `cache.freq_threshold` 次后，答案写入 Redis；
命中缓存时按 30 字符/批模拟流式输出，前端体验一致。缓存 TTL、阈值
均可在 AI 配置中心热调整。

## 3. 可调参数（AI 配置中心 / sys_ai_config 表）

| 参数 | 默认 | 说明 |
|------|------|------|
| rag.vector_top_k | 24 | 向量召回上限 |
| rag.bm25_top_k | 24 | 关键词召回上限（配置键沿用，见 §2.1） |
| rag.rrf_top_n | 36 | 融合后保留数量 |
| rag.rerank_top_k | 8 | 重排序输出数量 |
| rag.rrf_k_constant | 60 | RRF 常数 K，越大头部优势越小 |
| safety.confidence_threshold | 0.45 | 低于此值触发兜底 |
| cache.freq_threshold | 2 | 触发缓存的最小提问次数 |
| cache.ttl_hours | 2 | 缓存保留时长 |
| llm.* | - | 模型名/温度/超时，热替换无需重启 |

## 4. 数据入库流水线

```
上传（pdf/docx/doc/txt）
  → MinIO 存原始文件 + 本地 uploads/ 存处理副本
  → DocumentExtractor 抽取文本（PDF 按页保留页码）
  → TextChunker 切块（目标 600 字，上限 900，重叠 120；
     识别中文章节标题写入 chapter，内容类型打标 definition/symptom/treatment/drug/case）
  → Embedding 批量向量化（批大小 10）
  → Milvus 入库（含 category/chapter/page_number/knowledge_base_id 元数据）
```

## 5. 检索质量评估

见 `evaluation/eval_retrieval.py`。基于 `sample-data/` 的 PubMed 样例库
与 11 科室病例评测集做离线词法评估（Recall@K / MRR@K），用于对比
切块策略或参数调整的相对效果。更严格的语义评估建议接入 Ragas。
