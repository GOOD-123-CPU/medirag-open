#!/usr/bin/env python3
"""MediRAG 检索质量评估脚本（离线，不依赖运行中的服务）。

用途：
    基于 sample-data/authoritative_cases_11_departments.json 中的
    真实病例条目（AI 生成的中文摘要 + 检索关键词），构建一个轻量
    的词法评测集，对本地知识库 JSON 做召回评估。

指标：
    - Recall@K   ：前 K 个结果中是否命中至少一条 gold 文档
    - MRR@K      ：gold 文档首条命中排名的倒数均值

注意：
    这是面向 demo/教学场景的轻量评估（词法匹配，非语义召回），
    用于在更换切块策略 / 检索参数时对比相对效果。
    更严格的语义评估建议接入 Ragas 或自建评测集。

用法：
    python evaluation/eval_retrieval.py \
        --kb sample-data/medirag_knowledge_sample.json \
        --cases sample-data/authoritative_cases_11_departments.json \
        --top-k 10
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def tokenize(text: str) -> List[str]:
    """极简中英混合分词：英文按词，中文按 2-gram + 关键整词。"""
    tokens: List[str] = []
    for m in re.finditer(r"[A-Za-z][A-Za-z0-9+\-_]*", text):
        tokens.append(m.group().lower())
    zh = re.sub(r"[^\u4e00-\u9fff]+", " ", text)
    for part in zh.split():
        if len(part) <= 4:
            tokens.append(part)
        else:
            tokens.extend(part[i:i + 2] for i in range(0, len(part) - 1))
    return [t for t in tokens if len(t) >= 2]


def build_documents(kb: Dict) -> List[Dict]:
    """知识库 JSON -> 文档列表 [{id, text}]。兼容两种结构。"""
    docs: List[Dict] = []
    records = kb.get("records") or kb.get("documents") or []
    for i, rec in enumerate(records):
        text = " ".join(str(rec.get(k, "")) for k in ("title", "content", "abstract", "text"))
        if text.strip():
            docs.append({"id": rec.get("pmid") or rec.get("id") or f"doc-{i}", "text": text})
    return docs


def score_doc(query_terms: List[str], doc_text: str) -> float:
    """词法覆盖打分：命中词权重 / 查询词总权重（词越长权重越高）。"""
    if not query_terms:
        return 0.0
    text = doc_text.lower()
    total = matched = 0.0
    for t in query_terms:
        w = min(4.0, max(1.2, len(t) / 2.0))
        total += w
        if t in text:
            matched += w
    return matched / total if total else 0.0


def evaluate(cases: List[Dict], docs: List[Dict], top_k: int) -> Dict:
    recalls, rr = [], []
    for case in cases:
        kws = case.get("retrieval_keywords") or []
        summary = case.get("clinical_summary_zh", "")
        query_terms = tokenize(" ".join(kws) + " " + summary)
        if not query_terms or not docs:
            continue
        ranked = sorted(docs, key=lambda d: score_doc(query_terms, d["text"]), reverse=True)[:top_k]

        gold_terms = [k.lower() for k in kws if len(k) >= 2]
        hit_rank = None
        for rank, doc in enumerate(ranked, start=1):
            text = doc["text"].lower()
            if any(g in text for g in gold_terms):
                hit_rank = rank
                break

        recalls.append(1.0 if hit_rank else 0.0)
        rr.append(1.0 / hit_rank if hit_rank else 0.0)

    n = max(1, len(recalls))
    return {
        "queries_evaluated": len(recalls),
        f"recall@{top_k}": round(sum(recalls) / n, 4),
        f"mrr@{top_k}": round(sum(rr) / n, 4),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="MediRAG offline retrieval evaluation")
    parser.add_argument("--kb", type=Path, required=True, help="知识库 JSON 文件")
    parser.add_argument("--cases", type=Path, required=True, help="评测病例 JSON 文件")
    parser.add_argument("--top-k", type=int, default=10)
    args = parser.parse_args()

    kb = load_json(args.kb)
    cases_raw = load_json(args.cases)
    cases = cases_raw["departments"] if isinstance(cases_raw, dict) and "departments" in cases_raw else cases_raw
    docs = build_documents(kb)

    if not docs:
        print("[WARN] 知识库中未解析出文档，请检查 JSON 结构（需含 records/documents 数组）", file=sys.stderr)
        return 1

    metrics = evaluate(cases, docs, args.top_k)
    print(json.dumps(metrics, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
