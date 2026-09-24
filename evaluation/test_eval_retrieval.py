import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("eval_retrieval.py")
SPEC = importlib.util.spec_from_file_location("eval_retrieval", MODULE_PATH)
eval_retrieval = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(eval_retrieval)


class RetrievalEvaluationContractTest(unittest.TestCase):
    def setUp(self):
        self.docs = [
            {"id": "d1", "text": "acute appendicitis right lower quadrant pain fever surgery"},
            {"id": "d2", "text": "DKA hyperglycemia ketones metabolic emergency"},
            {"id": "d3", "text": "sleep exercise hydration general health"},
        ]

    def test_explicit_labels_report_true_recall_and_mrr(self):
        cases = [
            {
                "query": "appendicitis right lower quadrant pain",
                "retrieval_keywords": ["appendicitis", "right lower quadrant"],
                "relevant_doc_ids": ["d1"],
            },
            {
                "query": "DKA hyperglycemia ketones",
                "retrieval_keywords": ["DKA", "hyperglycemia"],
                "relevant_doc_ids": ["d2"],
            },
        ]
        metrics = eval_retrieval.evaluate(cases, self.docs, top_k=1)
        self.assertEqual(metrics["label_mode"], "explicit_doc_ids")
        self.assertEqual(metrics["recall@1"], 1.0)
        self.assertEqual(metrics["mrr@1"], 1.0)
        self.assertNotIn("proxy_hit_rate@1", metrics)

    def test_proxy_mode_never_calls_metric_recall(self):
        cases = [
            {
                "clinical_summary_zh": "appendicitis emergency",
                "retrieval_keywords": ["appendicitis"],
            }
        ]
        metrics = eval_retrieval.evaluate(cases, self.docs, top_k=1)
        self.assertEqual(metrics["label_mode"], "heuristic_keyword_proxy")
        self.assertIn("proxy_hit_rate@1", metrics)
        self.assertNotIn("recall@1", metrics)

    def test_mixed_label_contract_is_rejected(self):
        cases = [
            {"query": "appendicitis", "retrieval_keywords": ["appendicitis"], "relevant_doc_ids": ["d1"]},
            {"query": "DKA", "retrieval_keywords": ["DKA"]},
        ]
        with self.assertRaises(ValueError):
            eval_retrieval.evaluate(cases, self.docs, top_k=2)

    def test_recall_counts_all_relevant_documents(self):
        cases = [
            {
                "query": "appendicitis emergency",
                "retrieval_keywords": ["appendicitis"],
                "relevant_doc_ids": ["d1", "d2"],
            }
        ]
        metrics = eval_retrieval.evaluate(cases, self.docs, top_k=1)
        self.assertEqual(metrics["recall@1"], 0.5)


if __name__ == "__main__":
    unittest.main()
