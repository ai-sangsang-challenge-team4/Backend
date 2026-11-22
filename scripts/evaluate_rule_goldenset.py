#!/usr/bin/env python3
"""Offline Rule Detector evaluation; production Java rules, no server, DB or LLM.

Uses two *unchanged* Golden Set files from the adjacent AI repo. The outputs
match AI's three-way report reader contract and preserve full Rule candidates
for a future controlled Rule+LLM run. All evaluation artifacts are new files.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import subprocess
import tempfile
from collections import Counter
from pathlib import Path

CODES = ("PROFANITY", "INSULT", "THREAT", "LEGAL", "EXPOSURE",
         "REPEAT", "UNFAIR_REQUEST", "PRIVACY")
EVALUATED = tuple(code for code in CODES if code != "REPEAT")
STATES = {"PRESENT", "ABSENT", "BOUNDARY", "UNKNOWN"}
POLICIES = {"present": {"PRESENT"}, "present_boundary": {"PRESENT", "BOUNDARY"}}
DATA_FILES = ("TeacherCare_goldenset_200cases.csv",
              "TeacherCare_goldenset_200risktages.csv")


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def json_hash(value):
    return hashlib.sha256(json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")).hexdigest()


def read_rows(path):
    with Path(path).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path, rows, fields):
    with path.open("x", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def load_gold(dataset_dir):
    paths = [dataset_dir / name for name in DATA_FILES]
    if any(not path.is_file() for path in paths):
        raise ValueError("Both exact Golden Set CSVs are required")
    cases = read_rows(paths[0])
    labels = read_rows(paths[1])
    if len(cases) != 200 or len(labels) != 1600:
        raise ValueError("Golden Set requires 200 complaints and 1,600 tags")
    ids = [row["case_id"] for row in cases]
    if len(set(ids)) != 200 or any(not row["message_text"].strip() for row in cases):
        raise ValueError("Blank/duplicate Golden Set complaints")
    gold = {}
    for row in labels:
        key = (row["case_id"], row["riskTagCode"])
        if key in gold or row["riskTagCode"] not in CODES or row["state"] not in STATES:
            raise ValueError("Invalid or duplicate Golden Set tag")
        gold[key] = row["state"]
    if set(gold) != {(case_id, code) for case_id in ids for code in CODES}:
        raise ValueError("Missing/orphan Golden Set labels")
    for case in cases:
        if any(case[code] != gold[(case["case_id"], code)] for code in CODES):
            raise ValueError("Golden Set wide/long labels disagree")
    hashes = {path.name: digest(path) for path in paths}
    return cases, gold, hashes, json_hash(hashes)


def build_results(cases, gold, by_case):
    if set(by_case) != {case["case_id"] for case in cases}:
        raise ValueError("Java Rule output case IDs differ from Golden Set")
    rows = []
    cases_out = []
    for case in cases:
        case_id = case["case_id"]
        tags = by_case[case_id]
        if not isinstance(tags, list) or len(tags) != 8:
            raise ValueError("Incomplete Rule output")
        predictions = {}
        for tag in tags:
            code, state = tag.get("code"), tag.get("state")
            if code not in CODES or code in predictions or state not in STATES:
                raise ValueError("Invalid Rule tag code/state")
            predictions[code] = tag
        if set(predictions) != set(CODES):
            raise ValueError("Missing Rule tag")
        mismatches = 0
        for code in CODES:
            tag = predictions[code]
            state = tag["state"]
            correct = int(state == gold[(case_id, code)])
            if code != "REPEAT":
                mismatches += 1 - correct
            rows.append({
                "case_id": case_id,
                "code": code,
                "gold_state": gold[(case_id, code)],
                "predicted_state": state,
                "request_success": 1,
                "evaluation_in_scope": int(code != "REPEAT"),
                "state_match": correct,
                "evidence_evaluable": 0,
                "evidence_exclusion_reason": "rule_only_no_evidence_comparison",
                "predicted_span_count": len(tag.get("evidenceSpans", [])),
                "grounded_span_count": sum(span.get("text", "") in case["message_text"]
                                           for span in tag.get("evidenceSpans", [])),
            })
        cases_out.append({"case_id": case_id, "request_success": 1,
                          "latency_ms": "", "mismatch_count": mismatches})
    return rows, cases_out


def binary_metric(rows, positives):
    known = [row for row in rows if row["gold_state"] != "UNKNOWN"]
    tp = sum(row["gold_state"] in positives and row["predicted_state"] in positives for row in known)
    fp = sum(row["gold_state"] not in positives and row["predicted_state"] in positives for row in known)
    fn = sum(row["gold_state"] in positives and row["predicted_state"] not in positives for row in known)
    tn = len(known) - tp - fp - fn
    return {"precision": tp/(tp+fp) if tp+fp else 0.0,
            "recall": tp/(tp+fn) if tp+fn else 0.0,
            "f1": 2*tp/(2*tp+fp+fn) if 2*tp+fp+fn else 0.0,
            "tp": tp, "fp": fp, "fn": fn, "tn": tn,
            "evaluated_labels": len(known)}


def score(rows):
    evaluated = [row for row in rows if row["evaluation_in_scope"]]
    output = {}
    for policy, positive in POLICIES.items():
        tags = {code: binary_metric([row for row in evaluated if row["code"] == code], positive)
                for code in EVALUATED}
        metrics = list(tags.values())
        micro = binary_metric(evaluated, positive)
        output[policy] = {
            "macro_precision": sum(m["precision"] for m in metrics)/len(metrics),
            "macro_recall": sum(m["recall"] for m in metrics)/len(metrics),
            "macro_f1": sum(m["f1"] for m in metrics)/len(metrics),
            "micro": micro, "tags": tags,
        }
    return output


def main(argv=None):
    parser = argparse.ArgumentParser(description="Backend production Rule Only, 200 Golden Set cases")
    parser.add_argument("--gold-dir", type=Path, default=Path("../AI/data/goldenset"))
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    if args.output_dir.exists():
        parser.error("output directory already exists; refusing to overwrite")
    repo = Path(__file__).resolve().parents[1]
    cases, labels, hashes, dataset_digest = load_gold(args.gold_dir)
    with tempfile.TemporaryDirectory(prefix="teacherhub_rule_") as tmp:
        input_path = Path(tmp) / "input.json"
        output_path = Path(tmp) / "rule_results.json"
        input_path.write_text(json.dumps([
            {"case_id": row["case_id"], "message_text": row["message_text"]} for row in cases
        ], ensure_ascii=False), encoding="utf-8")
        subprocess.run([
            str(repo / "gradlew"), "--quiet", "evaluateRuleGoldenset",
            "-PgoldInput=" + str(input_path),
            "-PruleOutput=" + str(output_path),
        ], cwd=repo, check=True)
        by_case = json.loads(output_path.read_text(encoding="utf-8"))
    rows, case_rows = build_results(cases, labels, by_case)
    metrics = score(rows)
    args.output_dir.mkdir(parents=True, exist_ok=False)
    output = args.output_dir
    (output / "rule_results.json").write_text(json.dumps(
        by_case, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_csv(output / "results.csv", rows, list(rows[0]))
    write_csv(output / "cases.csv", case_rows, list(case_rows[0]))
    (output / "metrics.json").write_text(json.dumps(
        metrics, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    artifacts = {p.name: digest(p) for p in output.iterdir() if p.is_file()}
    manifest = {
        "schema_version": 1,
        "mode": "rule_llm",  # expected by shared comparison reader; never means an LLM ran
        "execution": "offline_java_rule_only",
        "dataset_schema": "goldenset",
        "requested_cases": 200,
        "dataset": {"sha256": dataset_digest, "files": hashes},
        "backend_capture": {"prediction_view": "rule"},
        "rule": {"version": "v1.1", "sha256": digest(
            repo / "src/main/resources/risk/teachercare_rule_dictionary_v1.1_extended.csv")},
        "artifacts": artifacts,
        "note": "Rule Only, never calls LLM; REPEAT excluded from tagging metrics",
    }
    (output / "manifest.json").write_text(json.dumps(
        manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "requested_cases": 200, "successful_cases": 200,
        "evaluated_tags": len(EVALUATED), "dataset_sha256": dataset_digest,
        "present_macro_f1": metrics["present"]["macro_f1"],
        "candidate_macro_f1": metrics["present_boundary"]["macro_f1"],
        "rule_results_json": str(output / "rule_results.json"),
        "comparison_run_directory": str(output),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
