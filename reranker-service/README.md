# MediRAG Reranker 微服务

基于 `sentence-transformers` Cross-Encoder 的重排序 HTTP 微服务，
为后端 `CrossEncoderReranker` 提供远程 rerank 端点。

- 默认模型：`BAAI/bge-reranker-v2-m3`（可经环境变量覆盖）
- API 格式：兼容 DashScope/GTE rerank 请求与响应
- 首次启动会从 HuggingFace 下载模型（约 2GB），请耐心等待

## 接口

`POST /rerank`

```json
{
  "model": "bge-reranker-v2-m3",
  "input": {
    "query": "急性阑尾炎的典型表现",
    "documents": ["文档1内容", "文档2内容"]
  },
  "parameters": { "top_n": 5, "return_documents": false }
}
```

响应：

```json
{
  "output": {
    "results": [
      { "index": 0, "relevance_score": 0.98 },
      { "index": 2, "relevance_score": 0.41 }
    ]
  }
}
```

另提供 `GET /health` 健康检查。

## 本地运行

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8001
```

## 后端接入

在 `.env` 中配置：

```
RERANKER_API_URL=http://localhost:8001/rerank
RERANKER_MODEL=bge-reranker-v2-m3
```

不配置时后端自动降级为本地词法混合打分，链路不受影响。
