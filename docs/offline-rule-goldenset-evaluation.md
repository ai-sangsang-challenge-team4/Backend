# Golden Set 200건 Rule Only 위험요소 태깅 재평가

## 목적

기존 운영 구현 `DictionaryRuleBasedRiskDetector`와 원본 `RiskRuleCsvLoader`의
72개 사전 규칙을 **수정 없이** 사용해 골든셋 200건의 Rule Only 위험요소 태깅을 재평가합니다.

- **AI/LLM 호출 없음**; HTTP, Spring Context, 서비스 DB, 로그인 및 민원 초안 생성 없음
- 태그 8종 모두 출력, `REPEAT`는 이력이 없으므로 평가 지표에서 제외 → 7종
- `PRESENT`와 `PRESENT+BOUNDARY` 이진 양성 정책 모두 평가
- 무매칭 태그는 `ABSENT`; 매칭 후보의 `BOUNDARY`/`UNKNOWN`은 탐지기 원본 상태 유지
- 결과는 다른 모델의 성능 값으로 합성하지 않고, Rule 원본 출력만 기록

## 실행 전 확인

Backend와 AI 저장소가 다음처럼 나란히 존재해야 합니다.

```text
~/Desktop/
├── Backend/
└── AI/
    └── data/goldenset/
        ├── TeacherCare_goldenset_200cases.csv
        └── TeacherCare_goldenset_200risktages.csv
```

Backend 브랜치 `feat/offline-rule-goldenset-evaluation`을 체크아웃하고
Java 17, Python 3.11 이상 환경에서 저장소 루트에서 실행합니다.

```bash
cd ~/Desktop/Backend
git fetch origin
git switch feat/offline-rule-goldenset-evaluation
git pull origin feat/offline-rule-goldenset-evaluation

./gradlew test --tests '*DictionaryRuleBasedRiskDetectorTest' \
  --tests '*RuleGoldensetExportMainTest'
python3 scripts/test_evaluate_rule_goldenset.py

# 출력 경로는 아직 존재하지 않아야 합니다.
python3 scripts/evaluate_rule_goldenset.py \
  --gold-dir ../AI/data/goldenset \
  --output-dir ../AI/data/private/rule-only-g200-20261122
```

실행은 약 200건의 **오프라인 Java 탐지**만 수행하므로 Claude 비용이 없습니다.
서버나 로컬 DB를 켜지 않아도 됩니다.

## 산출물

```text
AI/data/private/rule-only-g200-20261122/
├── rule_results.json  # case_id → 8종 Rule 출력 배열; Rule+LLM 입력에 사용
├── results.csv        # 태그별 정답 상태/예측 상태, 공통 비교기 호환
├── cases.csv          # 200건 성공 여부
├── metrics.json       # 7종의 Macro/Micro P/R/F1, 태그별 TP/FP/FN/TN
└── manifest.json      # 골든셋·규칙 파일 SHA-256, 결과 해시, Rule 실행 표식
```

`rule_results.json`은 AI 평가기 `--mode rule_llm --rule-results`의 입력 계약을
따릅니다. `REPEAT`도 원본 상태로 보존하지만 성능 지표는 7종만 산출합니다.
`manifest.mode=rule_llm`은 **공용 비교기와의 입력 형식 호환을 위한 값일 뿐**이며,
`execution=offline_java_rule_only`와 `backend_capture.prediction_view=rule`이
이 출력이 실제 Rule Only임을 명시합니다. **이 파일을 Rule+LLM 실측 결과로 해석하면 안 됩니다.**

## 이후 LLM Only 및 Rule+LLM 비교

AI 저장소에서 기존 독립 LLM Only 평가 폴더와 이번 Rule 폴더를 재사용합니다.
세 번째 Rule+LLM 결과를 확보하면 AI PR #59 비교 도구로 계산합니다.

```bash
cd ~/Desktop/AI
python scripts/compare_goldenset_three_way.py \
  --rule-run data/private/rule-only-g200-20261122 \
  --llm-run reports/g200-llm-only/<실제_LLM_ONLY_실행ID> \
  --rule-llm-run reports/g200-rule-llm/<실제_RULE_LLM_실행ID> \
  --output reports/g200-three-way/comparison.json
```

세 실행은 같은 원본 CSV 파일 해시와 200개 사례 ID를 사용해야 합니다.
서버의 Claude 모델 버전, RAG·마스킹·프롬프트 설정이 다른 경우 통제된
A/B 효과로 단정하지 않습니다. Risk Engine의 위험도(LOW/MEDIUM/HIGH/EMERGENCY)
평가는 이 실험의 범위에서 제외합니다.

## 유의 사항

- 이 평가는 기존 Backend Rule Only 보고서의 수치와 **다시 비교**하기 위한 것입니다.
  정확히 같은 Rule 사전 버전·활성 규칙·골든셋 해시인 경우에만 직접 비교할 수 있습니다.
- 서버 DB에서 규칙을 변경·비활성화했다면 이 도구의 72개 원본 사전 결과는
  실제 DB 규칙의 실행 결과와 다를 수 있습니다.
- 기존 출력 디렉터리 덮어쓰기를 거부합니다. 중복 실행 시 새 폴더명을 사용하세요.
- 민원 원문은 `results.csv`에 포함하지 않으나 `rule_results.json`에는
  근거 표현이 포함될 수 있습니다. `data/private/` 보관을 유지하고 Git에 올리지 마세요.
