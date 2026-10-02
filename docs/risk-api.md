# 민원 위험 분석 API

## 명세 변경

중간 LLM 결과를 따로 저장하는 모델이 없으므로 기존 `POST /complaints/{complaintId}/risk-tags` 단계는 `POST /complaints/{complaintId}/risk-analysis`에 통합한다. 클라이언트가 중간 태그나 점수를 전달하지 않는다. 서버가 저장된 원문을 기준으로 전체 분석을 수행한다.

모든 API는 Bearer 인증이 필요하며 민원 작성자만 접근할 수 있다. 현재 교사 조회 권한은 제공하지 않는다. POST 요청 본문은 없다.

| 메서드 | 경로 | 동작 | 성공 |
|---|---|---|---|
| POST | `/complaints/{complaintId}/mask` | 원문의 규칙 기반 개인정보 마스킹 결과 저장·반환 | 200 |
| POST | `/complaints/{complaintId}/risk-analysis` | 마스킹 → 룰·LLM 탐지 → 병합 → 점수 계산·저장 → 수정안 생성 | 200 |
| GET | `/risk-analyses/{analysisId}` | 저장된 분석의 점수·등급 조회 | 200 |
| GET | `/risk-analyses/{analysisId}/findings` | 최종 감지 태그·근거·태그별 수정 제안·최종 수정본 조회 | 200 |
| GET | `/risk-analyses/{analysisId}/detector-results` | 전체 태그의 Rule·LLM 감지 여부·출처별 근거·LLM 신뢰도 조회 | 200 |
| GET | `/risk-tags` | 위험 태그 기준 목록 조회, 민원 분석과 별개 | 200 |

## 마스킹 응답 예시

분석 요청은 `/complaints/{complaintId}/risk-analysis`로 통일하며 기존 `/review` 경로는 제공하지 않는다.

서비스는 `risk.service`에 세 개만 둔다.

- `RiskAnalysisService`: 전체 분석 실행·저장·조회. 태그 병합과 점수 계산은 RiskEvaluator로 처리한다.
- `ComplaintMaskingService`: 마스킹 API의 민원 조회·권한/상태 검증·마스킹 결과 저장.
- `RiskTagService`: 위험 태그 기준 목록 조회.

룰 탐지기·LLM 분석기·수정안 생성기 인터페이스 및 `risk.masking`의 마스킹 구현은 별도로 유지한다.

```json
{
  "complaintId": 42,
  "contentVersion": 1,
  "maskedContent": "[전화번호]로 연락 부탁드립니다."
}
```

전화번호(국내 휴대폰·일반 지역번호·070 및 일부 +82 형태), 이메일, 주민등록번호 형태를 정규식으로 치환한다. 이름·자유 형식 주소·계좌번호 등 모든 개인정보를 탐지하는 기능은 아니다. 200은 지원 패턴의 치환 성공을 뜻하며 외부 AI 전송에 안전하다는 보장은 아니다. 불확실성을 판정하는 로직이 없으므로 기존 명세의 422는 현재 제공하지 않는다.

원문은 변경하지 않는다. 민원 수정 시 기존 maskedContent는 초기화된다. 전체 분석 API는 항상 현재 원문을 다시 마스킹하므로 mask API의 선행 호출은 선택 사항이다. 룰의 탐지 근거도 마스킹 후 저장·수정안 생성에 사용한다.

## 분석 실행 응답

POST `/complaints/{complaintId}/risk-analysis`와 GET `/risk-analyses/{analysisId}`는
동일한 `FinalRiskResult`를 반환한다.

```json
{
  "analysisId": 10,
  "complaintId": 42,
  "riskScore": 5,
  "riskLevel": "HIGH"
}
```

POST는 마스킹, Rule·LLM 탐지, 병합, 점수 계산과 수정안 생성·저장을 수행한다.
응답에는 요약만 포함하며, 근거와 태그별 수정 제안 및 전체 수정본은
`GET /risk-analyses/{analysisId}/findings`로 조회한다.
Rule·LLM 상세는 `GET /risk-analyses/{analysisId}/detector-results`로 조회한다.
같은 내용 버전으로 분석을 다시 요청하면 기존 분석의 요약을 반환하며 AI를 다시 호출하지 않는다.
과거 ID 조회는 해당 분석 시점의 결과다. `ComplaintReviewResponse`는 삭제했다.
처리 경로·반복 이력 가산·권장 조치는 아직 구현되지 않았다.

## 재분석 및 최종 제출

- 최초 분석 1회 + 재분석 1회, 민원별 성공한 분석 최대 2회.
- 최초 분석은 수정안을 생성하고, 전송 시 분석은 위험도만 확정한다. 처리 중에는 DRAFT이며 실패 시 제출 전체가 롤백된다.
- 동일 내용 저장은 contentVersion을 올리지 않는다. 같은 버전 분석 재요청은 저장 결과를 반환하고 AI 호출/횟수를 추가하지 않는다.
- PATCH 후 바로 POST send를 호출한다. 서버는 최종 문장이 분석 원문과 같으면 재사용하고, 다르면 한 번 재분석한다. 수정했다가 원문으로 되돌린 경우도 재사용한다.
- 수정 후 POST risk-analysis 호출은 409 FINAL_ANALYSIS_ON_SUBMIT. 최종 재분석은 send 내부에서만 실행하고 새 수정안을 생성하지 않는다. 최대 성공 분석 2회 제한은 유지한다.
- POST `/complaints/{complaintId}/send`: Bearer 인증 필요, 본문 없음. 최초 분석이 없으면 409 ANALYSIS_REQUIRED. 변경된 내용의 최종 분석은 서버가 자동 처리하며 성공 후에만 교사가 지정된다.
- 최종 제출 성공 시 200이며 상태 및 이력은 DRAFT → ANALYZED. 최초 분석 후 수정하지 않은 경우에도 바로 제출할 수 있다.
- 분석·수정·제출은 동일 민원 행의 비관적 쓰기 잠금으로 동시 실행을 직렬화한다. 현재 동기 처리이므로 AI 호출 동안 잠금이 유지된다.

DB에는 risk_analyses의 original_content/completed/revision_reason/ai_revision 컬럼과 risk_analysis_expressions 테이블이 추가된다. 기존 분석에는 전체 재사용 응답이 없으므로 completed 기본값은 false이며 재사용·완료 횟수에서 제외된다. 기존 데이터를 유지하는 배포에서는 이 정책을 고려해 스키마를 반영해야 한다.

## 오류

| 상태 | 조건 |
|---|---|
| 400 | 마스킹/분석할 원문이 비어 있음, 경로 ID 형식 오류 |
| 401 | 인증되지 않았거나 인증 사용자 정보가 없음 |
| 403 | 다른 작성자의 민원·분석 접근 |
| 404 | 민원 또는 분석이 없음 |
| 409 | DRAFT가 아닌 민원에 마스킹/분석 요청 |

## 현재 구현 범위

### 업무 예외 응답

서비스는 HTTP 상태를 지정하지 않고 도메인 예외를 발생시킨다. `GlobalExceptionHandler`가 다음과 같이 공통 `ErrorResponse`로 변환한다. 인증 필터에서 발생하는 오류는 별도의 Security 처리 경로를 따른다.

| 예외 | HTTP 상태 | 오류 코드 |
|---|---|---|
| EmptyComplaintContentException | 400 | EMPTY_COMPLAINT_CONTENT |
| UnauthorizedException | 401 | UNAUTHORIZED |
| ComplaintAccessDeniedException | 403 | COMPLAINT_ACCESS_DENIED |
| ComplaintNotFoundException | 404 | COMPLAINT_NOT_FOUND |
| RiskAnalysisNotFoundException | 404 | RISK_ANALYSIS_NOT_FOUND |
| ComplaintNotDraftException | 409 | COMPLAINT_NOT_DRAFT |

```json
{"status":"ERROR","code":"COMPLAINT_ACCESS_DENIED","message":"해당 민원에 접근할 권한이 없습니다.","timestamp":"2026-10-01T09:00:00+09:00","path":"/complaints/42/mask"}
```

마스킹은 실제 백엔드 규칙 처리다. 룰 탐지기·LLM 분석기·수정안 생성기는 여전히 Mock이므로 실제 위험 판단/문맥 수정은 하지 않는다. 점수 계산과 분석 결과 저장·조회는 구현되어 있다. 외부 LLM 연동 전에 이름 등 미지원 개인정보 처리 정책을 별도로 정해야 한다.

## 분리된 조회 응답

`FinalRiskResult`는 analysisId, complaintId, riskScore, riskLevel만 반환한다.
내부 수정안 생성 입력은 `ComplaintRevisionGenerator.RiskEvaluation`으로 분리했다. POST 응답도 이 요약 DTO다.
수정안 생성 결과는 내부 ComplaintRevisionGenerator.RevisionResult로 받는다.
전체 수정본은 RiskFindingsResponse.FinalRevision을 재사용하고, 태그별 제안과 함께 DB에 저장한 뒤 findings 조회로 반환한다.
ComplaintRevisionResult.java는 삭제했으며 생성 결과에서 complaintId도 제거했다.

`GET /risk-analyses/{analysisId}/findings`:
```json
{
  "analysisId": 10,
  "findings": [{
    "code": "THREAT",
    "evidences": ["문제 표현"],
    "revisionSuggestions": [{
      "originalExpression": "문제 표현",
      "suggestedExpression": "수정 표현",
      "reason": "수정 이유"
    }]
  }],
  "finalRevision": {"reason": "전체 수정 이유", "content": "전체 수정본"}
}
```

findings는 finalDetected=true인 태그만 반환한다. 감지가 없으면 빈 배열이다.
수정 제안은 분석 POST에서 생성·저장하며 GET에서 AI를 호출하지 않는다.
최종 제출 재분석은 기존 정책대로 수정안을 생성하지 않아 revisionSuggestions는 빈 배열,
finalRevision은 null이다. 최초 분석과 제출 분석은 서로 다른 분석 ID로 조회한다.
탐지기와 수정안 생성기는 아직 Mock이므로 실제 문맥에 맞는 수정은 외부 AI 연동이 필요하다.

detector-results는 results(code, finalDetected, rule, llm)와 llm(modelName, temperature, reason)을 반환한다.
rule/llm에는 detected, confidence, evidences가 있다. 규칙 탐지 confidence는 null이다.
모든 위험 코드(미감지 포함)를 반환한다. 기존 소유권 검증과 401/403/404 정책은 세 조회 모두 적용한다.
미완료 분석은 조회하지 않는다.

### 스키마 변경

- risk_tag_revision_suggestions: id, risk_analysis_id(FK), code, original_expression,
  suggested_expression, reason, suggestion_order. RiskAnalysis의 cascade/orphanRemoval로 함께 관리한다.
- complaint_risk_tags: rule_evidence, llm_evidence(TEXT, nullable) 추가.
  기존 evidence는 분석 당시 병합 근거로 유지한다. 기존 confidence는 LLM 신뢰도다.
- risk_analyses의 revision_reason/ai_revision은 전체 수정본으로 유지한다.

기존 데이터를 유지하는 배포에서는 위 스키마 변경을 반영해야 한다.
기존 병합 evidence만으로 출처를 복원할 수 없으므로 기존 행의 출처별 근거는 빈 배열,
태그별 수정 제안도 빈 배열로 반환한다. 과거 기록을 다시 분석하여 채우지 않는다.

### 보조 DTO 파일 정리

API 응답 구조를 유지하면서 보조 타입을 사용하는 클래스 안으로 통합했다.

- RiskEvaluation → ComplaintRevisionGenerator.RiskEvaluation
- FinalRiskTagResponse → RiskEvaluator.FinalRiskTagResponse
- TagRevisionSuggestion → ComplaintRevisionGenerator.TagRevisionSuggestion

위 세 타입의 별도 DTO 파일은 삭제했다. 기본 위험도·근거·탐지기 상세 응답 DTO는 각각 유지한다.
