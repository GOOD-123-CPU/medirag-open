"""从 PubMed E-utilities 构建大规模权威知识库。

合规须知（使用前必读）：
- 本脚本仅抓取 NCBI E-utilities 开放接口提供的书目元数据
  （标题/期刊/年份/DOI/摘要），并通过 PMID 标注可溯源性。
- 请遵守 NCBI 使用政策（https://www.ncbi.nlm.nih.gov/books/NBK25497/）：
  - 不超过 3 请求/秒（无 API Key 时更保守，脚本默认 sleep 控制）
  - 提供 --email 联系方式便于 NCBI 限流时联系
- 摘要版权归原出版方，二次分发请以"元数据+链接"为主，
  商业化或大规模再分发前请自行评估版权风险。
"""
from __future__ import annotations

import argparse
import json
import math
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlencode
from urllib.request import urlopen
import xml.etree.ElementTree as ET

from docx import Document
from docx.oxml.ns import qn
from docx.shared import Pt

ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "知识库" / "权威扩容知识库"

DEPARTMENT_QUERIES = {
    "内科": "internal medicine case report",
    "外科": "surgery case report",
    "儿科": "pediatric case report",
    "妇科": "gynecology case report",
    "神经科": "neurology case report",
    "骨科": "orthopedics case report",
    "皮肤科": "dermatology case report",
    "眼科": "ophthalmology case report",
    "药学": "clinical pharmacy adverse drug reaction case report",
    "急诊科": "emergency medicine case report",
    "综合科": "fever of unknown origin case report"
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build large-scale authoritative KB from PubMed abstracts.")
    parser.add_argument("--target-pages", type=int, default=1000, help="Target Word-equivalent pages")
    parser.add_argument("--records-per-dept", type=int, default=1200, help="Upper cap per department")
    parser.add_argument("--email", default="", help="Optional contact email for NCBI E-utilities")
    parser.add_argument("--api-key", default="", help="Optional NCBI API key")
    parser.add_argument("--sleep-ms", type=int, default=120, help="Sleep between requests to avoid throttling")
    return parser.parse_args()


def eutils_get(endpoint: str, params: dict[str, Any], sleep_ms: int) -> bytes:
    url = f"https://eutils.ncbi.nlm.nih.gov/entrez/eutils/{endpoint}?{urlencode(params)}"
    with urlopen(url, timeout=30) as resp:
        data = resp.read()
    time.sleep(max(0, sleep_ms) / 1000.0)
    return data


def search_pmids(query: str, retmax: int, email: str, api_key: str, sleep_ms: int) -> list[str]:
    params = {
        "db": "pubmed",
        "term": query,
        "retmax": str(retmax),
        "retmode": "json",
        "sort": "relevance"
    }
    if email:
        params["email"] = email
    if api_key:
        params["api_key"] = api_key

    raw = eutils_get("esearch.fcgi", params, sleep_ms).decode("utf-8")
    data = json.loads(raw)
    return data["esearchresult"].get("idlist", [])


def fetch_pubmed_xml(pmids: list[str], email: str, api_key: str, sleep_ms: int) -> ET.Element:
    params = {
        "db": "pubmed",
        "id": ",".join(pmids),
        "retmode": "xml"
    }
    if email:
        params["email"] = email
    if api_key:
        params["api_key"] = api_key
    raw = eutils_get("efetch.fcgi", params, sleep_ms)
    return ET.fromstring(raw)


def article_to_record(article: ET.Element, department: str) -> dict[str, Any]:
    pmid = article.findtext(".//PMID", default="").strip()
    title = article.findtext(".//ArticleTitle", default="").strip()
    abstract_parts = [a.text.strip() for a in article.findall(".//AbstractText") if a.text and a.text.strip()]
    abstract = "\n".join(abstract_parts)
    journal = article.findtext(".//Journal/Title", default="").strip()
    year = article.findtext(".//PubDate/Year", default="").strip()
    doi = ""
    for aid in article.findall(".//ArticleId"):
        if aid.attrib.get("IdType", "").lower() == "doi" and aid.text:
            doi = aid.text.strip()
            break
    if not year:
        year = article.findtext(".//PubDate/MedlineDate", default="").strip()
    return {
        "department": department,
        "pmid": pmid,
        "title": title,
        "journal": journal,
        "year": year,
        "doi": doi,
        "url": f"https://pubmed.ncbi.nlm.nih.gov/{pmid}/" if pmid else "",
        "abstract": abstract
    }


def estimate_pages(records: list[dict[str, Any]]) -> int:
    words = 0
    for r in records:
        words += len((r.get("title", "") + " " + r.get("abstract", "")).split())
    # 粗略换算：英文500词约1页
    return max(1, math.ceil(words / 500))


def to_docx(records: list[dict[str, Any]], path: Path) -> None:
    doc = Document()
    style = doc.styles["Normal"]
    style.font.name = "Microsoft YaHei"
    style._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    style.font.size = Pt(10.5)

    doc.add_heading("权威文献扩容知识库（PubMed）", level=1)
    doc.add_paragraph("说明：本文件由 PubMed 摘要自动生成，含 PMID 与链接，便于追溯。")

    for i, r in enumerate(records, start=1):
        doc.add_heading(f"{i}. [{r['department']}] {r['title']}", level=2)
        doc.add_paragraph(f"期刊/年份：{r['journal']} ({r['year']})")
        doc.add_paragraph(f"PMID：{r['pmid']}")
        doc.add_paragraph(f"DOI：{r['doi'] or 'N/A'}")
        doc.add_paragraph(f"链接：{r['url']}")
        doc.add_paragraph("摘要：")
        doc.add_paragraph(r["abstract"] or "N/A")
    doc.save(path)


def main() -> None:
    args = parse_args()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    all_records: list[dict[str, Any]] = []
    for dept, query in DEPARTMENT_QUERIES.items():
        pmids = search_pmids(query, args.records_per_dept, args.email, args.api_key, args.sleep_ms)
        if not pmids:
            continue

        for start in range(0, len(pmids), 100):
            chunk = pmids[start:start + 100]
            root = fetch_pubmed_xml(chunk, args.email, args.api_key, args.sleep_ms)
            for article in root.findall(".//PubmedArticle"):
                record = article_to_record(article, dept)
                if record["title"] and record["pmid"]:
                    all_records.append(record)

            if estimate_pages(all_records) >= args.target_pages:
                break
        if estimate_pages(all_records) >= args.target_pages:
            break

    # 去重（按PMID）
    dedup: dict[str, dict[str, Any]] = {}
    for r in all_records:
        dedup[r["pmid"]] = r
    records = list(dedup.values())

    # 输出 JSON + DOCX
    json_path = OUTPUT_DIR / "pubmed_massive_kb.json"
    docx_path = OUTPUT_DIR / "pubmed_massive_kb.docx"
    meta_path = OUTPUT_DIR / "pubmed_massive_kb_meta.json"

    json_path.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    to_docx(records, docx_path)

    meta = {
        "record_count": len(records),
        "estimated_pages": estimate_pages(records),
        "target_pages": args.target_pages,
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "departments": list(DEPARTMENT_QUERIES.keys())
    }
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json_path)
    print(docx_path)
    print(meta_path)
    print(f"Estimated pages: {meta['estimated_pages']}")


if __name__ == "__main__":
    main()
