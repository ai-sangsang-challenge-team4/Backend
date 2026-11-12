#!/usr/bin/env bash
set -euo pipefail

ai_server_url="${AI_SERVER_URL:-http://localhost:8000}"
timeout_seconds="${AI_LIVE_TIMEOUT_SECONDS:-60}"
result_dir="$(mktemp -d)"
trap 'rm -rf -- "$result_dir"' EXIT

post_json() {
  local path="$1"
  local payload="$2"
  local output="$3"
  curl --fail --silent --show-error \
    --connect-timeout 2 \
    --max-time "$timeout_seconds" \
    --header 'Content-Type: application/json' \
    --data "$payload" \
    --output "$output" \
    "${ai_server_url}${path}"
}

systemctl is-active --quiet teacherhub-ai.service
curl --fail --silent --show-error \
  --connect-timeout 2 \
  --max-time 5 \
  --output /dev/null \
  "${ai_server_url}/"

post_json '/api/v1/ai/analyze' \
  '{"complaint":"상담 일정을 확인 부탁드립니다.","ruleResults":[]}' \
  "$result_dir/analyze.json"

post_json '/api/v1/ai/revision' \
  '{"complaint":"[이름] 선생님은 무능합니다.","tags":[{"code":"INSULT","state":"PRESENT","confidence":0.9,"evidence":"무능","reason":"모욕 표현"}],"riskScore":10,"riskLevel":"MEDIUM"}' \
  "$result_dir/revision.json"

post_json '/api/v1/ai/summary' \
  '{"content":"학부모가 상담 일정과 조치 내용을 확인해 달라고 요청했습니다."}' \
  "$result_dir/summary.json"

post_json '/api/v1/ai/response-draft' \
  '{"content":"PARENT_INQUIRY: 상담 일정과 조치 내용을 확인해 주세요."}' \
  "$result_dir/response-draft.json"

python3 - "$result_dir" <<'PY'
import json
import pathlib
import sys

result_dir = pathlib.Path(sys.argv[1])

def load(name):
    with (result_dir / name).open(encoding="utf-8") as handle:
        return json.load(handle)

analyze = load("analyze.json")
assert isinstance(analyze.get("tags"), list)
assert isinstance(analyze.get("metadata", {}).get("modelName"), str)

revision = load("revision.json")
assert isinstance(revision.get("finalRevision", {}).get("content"), str)
assert isinstance(revision.get("tagSuggestions"), list)

summary = load("summary.json")
assert isinstance(summary.get("summary"), str)
assert isinstance(summary.get("modelName"), str)

draft = load("response-draft.json")
assert isinstance(draft.get("content"), str)
assert isinstance(draft.get("modelName"), str)
PY

printf '%s\n' 'AI live smoke passed: analyze, revision, summary, response-draft'
