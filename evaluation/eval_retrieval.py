#!/usr/bin/env python3
"""MediRAG offline retrieval evaluation.

Two evaluation contracts are deliberately separated:

1. Explicit relevance labels (relevant_doc_ids on every case)
   -> Recall@K and MRR@K are reported.
2. Legacy keyword proxy labels
   -> only proxy_hit_rate@K and proxy_mrr@K are reported.

The second mode is useful as a deterministic smoke/regression signal, but it is
not an independent semantic-retrieval benchmark because ranking and relevance
both depend on lexical evidence.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, Iterable, List


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def tokenize(text: str) -> List[str]:
    """Minimal mixed Chinese/English tokenizer for the offline lexical baseline."""
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
    """Knowledge-base JSON -> [{id, text}]."""
    docs: List[Dict] = []
    records = kb.get("records") or kb.get("documents") or []
    for i, rec in enumerate(records):
        text = " ".join(str(rec.get(k, "")) for k in ("title", "content", "abstract", "text"))
        if text.strip():
            docs.append({
                "id": str(rec.get("pmid") or rec.get("id") or f"doc-{i}"),
                "text": text,
            })
    return docs


def score_doc(query_terms: List[str], doc_text: str) -> float:
    """Lexical coverage baseline: matched query-term weight / total query weight."""
    if not query_terms:
        return 0.0
    text = doc_text.lower()
    total = matched = 0.0
    for term in query_terms:
        weight = min(4.0, max(1.2, len(term) / 2.0))
        total += weight
        if term in text:
            matched += weight
    return matched / total if total else 0.0


def _query_terms(case: Dict) -> List[str]:
    query = case.get("query") or case.get("clinical_summary_zh") or ""
    keywords = case.get("retrieval_keywords") or []
    return tokenize(" ".join(str(k) for k in keywords) + " " + str(query))


def rank_case(case: Dict, docs: List[Dict], top_k: int) -> List[Dict]:
    terms = _query_terms(case)
    return sorted(docs, key=lambda d: score_doc(terms, d["text"]), reverse=True)[:top_k]


def _explicit_relevant_ids(case: Dict) -> set[str] | None:
    raw = case.get("relevant_doc_ids")
    if raw is None:
        return None
    if not isinstance(raw, list):
        raise ValueError("relevant_doc_ids must be a list when provided")
    return {str(doc_id) for doc_id in raw}


def _first_relevant_rank(ranked: Iterable[Dict], relevant: set[str]) -> int | None:
    for rank, doc in enumerate(ranked, start=1):
        if str(doc["id"]) in relevant:
            return rank
    return None


def evaluate_explicit(cases: List[Dict], docs: List[Dict], top_k: int) -> Dict:
    recalls: List[float] = []
    reciprocal_ranks: List[float] = []
    for case in cases:
        relevant = _explicit_relevant_ids(case)
        if relevant is None:
            raise ValueError("explicit evaluation requires relevant_doc_ids on every case")
        if not relevant:
            continue
        ranked = rank_case(case, docs, top_k)
        retrieved_ids = {str(doc["id"]) for doc in ranked}
        recalls.append(len(relevant & retrieved_ids) / len(relevant))
        first_rank = _first_relevant_rank(ranked, relevant)
        reciprocal_ranks.append(1.0 / first_rank if first_rank else 0.0)

    n = len(recalls)
    if n == 0:
        raise ValueError("no explicitly labelled queries were available for evaluation")
    return {
        "label_mode": "explicit_doc_ids",
        "queries_evaluated": n,
        f"recall@{top_k}": round(sum(recalls) / n, 4),
        f"mrr@{top_k}": round(sum(reciprocal_ranks) / n, 4),
    }


def evaluate_proxy(cases: List[Dict], docs: List[Dict], top_k: int) -> Dict:
    """Legacy keyword proxy; intentionally does not call the metric Recall."""
    hits: List[float] = []
    reciprocal_ranks: List[float] = []
    for case in cases:
        keywords = [str(k).lower() for k in case.get("retrieval_keywords", []) if len(str(k)) >= 2]
        terms = _query_terms(case)
        if not terms or not docs or not keywords:
            continue
        ranked = rank_case(case, docs, top_k)
        hit_rank = None
        for rank, doc in enumerate(ranked, start=1):
            text = doc["text"].lower()
            if any(keyword in text for keyword in keywords):
                hit_rank = rank
                break
        hits.append(1.0 if hit_rank else 0.0)
        reciprocal_ranks.append(1.0 / hit_rank if hit_rank else 0.0)

    n = len(hits)
    if n == 0:
        raise ValueError("no proxy-labelled queries were available for evaluation")
    return {
        "label_mode": "heuristic_keyword_proxy",
        "queries_evaluated": n,
        f"proxy_hit_rate@{top_k}": round(sum(hits) / n, 4),
        f"proxy_mrr@{top_k}": round(sum(reciprocal_ranks) / n, 4),
        "warning": "proxy metrics share lexical signals with ranking and are not independent semantic evaluation",
    }


def evaluate(cases: List[Dict], docs: List[Dict], top_k: int) -> Dict:
    labelled = [_explicit_relevant_ids(case) is not None for case in cases]
    if all(labelled) and labelled:
        return evaluate_explicit(cases, docs, top_k)
    if any(labelled):
        raise ValueError("mixed labelled/unlabelled cases are not allowed; use one evaluation contract per file")
    return evaluate_proxy(cases, docs, top_k)


def main() -> int:
    parser = argparse.ArgumentParser(description="MediRAG offline retrieval evaluation")
    parser.add_argument("--kb", type=Path, required=True, help="knowledge-base JSON")
    parser.add_argument("--cases", type=Path, required=True, help="evaluation cases JSON")
    parser.add_argument("--top-k", type=int, default=10)
    args = parser.parse_args()

    if args.top_k <= 0:
        parser.error("--top-k must be positive")

    kb = load_json(args.kb)
    cases_raw = load_json(args.cases)
    cases = cases_raw["departments"] if isinstance(cases_raw, dict) and "departments" in cases_raw else cases_raw
    if not isinstance(cases, list):
        print("[ERROR] cases JSON must contain a list or a departments list", file=sys.stderr)
        return 2

    docs = build_documents(kb)
    if not docs:
        print("[ERROR] no documents parsed from knowledge-base JSON", file=sys.stderr)
        return 1

    try:
        metrics = evaluate(cases, docs, args.top_k)
    except ValueError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 2

    print(json.dumps(metrics, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
