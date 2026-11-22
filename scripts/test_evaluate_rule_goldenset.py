"""No-network tests of offline Rule Golden Set result validation and metrics."""
from __future__ import annotations

import csv
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("evaluate_rule_goldenset.py")
spec = importlib.util.spec_from_file_location("evaluate_rule_goldenset", SCRIPT)
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


class RuleGoldensetOfflineTests(unittest.TestCase):
    def setUp(self):
        self.cases = [{"case_id": "G001", "message_text": "선생님, 숙제를 확인해주세요."}]
        self.gold = {("G001", code): "ABSENT" for code in evaluation.CODES}
        self.rule = {"G001": [{"code": code, "state": "ABSENT", "evidenceSpans": []}
                              for code in evaluation.CODES]}

    def test_rule_export_yields_eight_labels_seven_scored(self):
        rows, cases = evaluation.build_results(self.cases, self.gold, self.rule)
        self.assertEqual(len(rows), 8)
        self.assertEqual(sum(row["evaluation_in_scope"] for row in rows), 7)
        self.assertEqual(cases[0]["mismatch_count"], 0)
        metric = evaluation.score(rows)
        self.assertEqual(metric["present"]["micro"]["evaluated_labels"], 7)

    def test_present_policy_does_not_equate_rule_boundary_with_present(self):
        self.gold[("G001", "LEGAL")] = "PRESENT"
        for tag in self.rule["G001"]:
            if tag["code"] == "LEGAL":
                tag["state"] = "BOUNDARY"
        rows, _ = evaluation.build_results(self.cases, self.gold, self.rule)
        metrics = evaluation.score(rows)
        self.assertEqual(metrics["present"]["micro"]["fn"], 1)
        self.assertEqual(metrics["present"]["micro"]["tp"], 0)
        self.assertEqual(metrics["present_boundary"]["micro"]["tp"], 1)

    def test_unknown_gold_is_excluded_in_binary_metrics(self):
        self.gold[("G001", "PRIVACY")] = "UNKNOWN"
        rows, _ = evaluation.build_results(self.cases, self.gold, self.rule)
        metrics = evaluation.score(rows)
        self.assertEqual(metrics["present"]["micro"]["evaluated_labels"], 6)

    def test_rejects_incomplete_or_duplicate_tag_export(self):
        self.rule["G001"].pop()
        with self.assertRaisesRegex(ValueError, "Incomplete Rule output"):
            evaluation.build_results(self.cases, self.gold, self.rule)
        self.rule["G001"].append(self.rule["G001"][0])
        with self.assertRaisesRegex(ValueError, "Invalid Rule tag"):
            evaluation.build_results(self.cases, self.gold, self.rule)

    def test_dataset_digest_uses_same_sorted_json_as_ai_evaluator(self):
        hashes = {"cases.csv": "abcdef", "labels.csv": "123456"}
        import hashlib
        expected = hashlib.sha256(json.dumps(
            hashes, ensure_ascii=False, sort_keys=True,
            separators=(",", ":"), allow_nan=False,
        ).encode("utf-8")).hexdigest()
        self.assertEqual(evaluation.json_hash(hashes), expected)


if __name__ == "__main__":
    unittest.main()
