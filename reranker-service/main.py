"""MediRAG Reranker 微服务。

提供 DashScope 兼容格式的 /rerank 端点，供后端 CrossEncoderReranker 调用。
模型可经环境变量 RERANKER_MODEL 覆盖，默认 BAAI/bge-reranker-v2-m3。
"""
from __future__ import annotations

import logging
import os
from typing import List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("medirag-reranker")

MODEL_NAME = os.getenv("RERANKER_MODEL", "BAAI/bge-reranker-v2-m3")

app = FastAPI(title="MediRAG Reranker", version="1.0.0")
_model = None
_tokenizer = None


class RerankInput(BaseModel):
    query: str
    documents: List[str]


class RerankParameters(BaseModel):
    top_n: int | None = None
    return_documents: bool = False


class RerankRequest(BaseModel):
    model: str | None = None
    input: RerankInput
    parameters: RerankParameters = Field(default_factory=RerankParameters)


def _load_model():
    """懒加载 CrossEncoder，避免 import 阶段阻塞容器探针。"""
    global _model, _tokenizer
    if _model is not None:
        return
    try:
        from sentence_transformers import CrossEncoder

        logger.info("Loading rerank model: %s (first call may download ~2GB)", MODEL_NAME)
        _model = CrossEncoder(MODEL_NAME, max_length=512)
        logger.info("Rerank model loaded")
    except Exception as exc:  # pragma: no cover
        logger.exception("Failed to load rerank model")
        raise HTTPException(status_code=503, detail=f"model load failed: {exc}") from exc


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_NAME, "loaded": _model is not None}


@app.post("/rerank")
def rerank(req: RerankRequest) -> dict:
    docs = req.input.documents
    query = req.input.query
    if not query or not docs:
        raise HTTPException(status_code=400, detail="query and documents are required")

    _load_model()
    pairs = [[query, doc] for doc in docs]
    scores = _model.predict(pairs)

    top_n = req.parameters.top_n or len(docs)
    order = sorted(range(len(docs)), key=lambda i: float(scores[i]), reverse=True)[:top_n]

    results = [
        {"index": i, "relevance_score": float(scores[i])}
        for i in order
    ]
    if req.parameters.return_documents:
        for item in results:
            item["document"] = docs[item["index"]]

    return {"output": {"results": results}}
