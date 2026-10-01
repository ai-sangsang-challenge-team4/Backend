# 민원 위험 분석 API

## 명세 변경

중간 LLM 결과를 따로 저장하는 모델이 없으므로 기존 `POST /complaints/{complaintId}/risk-tags` 단계는 `POST /complaints/{complaintId}/risk-analysis`에 통합한다. 클라이언트가 중간 태그나 점수를 전달하지 않는다. 서버가 저장된 원문을 기준으로 전체 분석을 수행한다.

모든 API는 Bearer 인증이 필요하며 민원 작성자만 접근할 수 있다. 현재 교사 조회 권한은 제공하지 않는다. POST 요청 본문은 없다.

| 메서드 | 경로 | 동작 | 성공 |
|---|---|---|---|
| POST | `/complaints/{complaintId}/mask` | 원문의 규칙 기반 개인정보 마스킹 결과 저장·반환 | 200 |
| POST | `/complaints/{complaintId}/risk-analysis` | 마스킹 → 룰·LLM 탐지 → 병합 → 점수 계산·저장 → 수정안 생성 | 200 |
| GET | `/risk-analyses/{analysisId}` | 저장된 분석의 점수·등급·태그·근거 조회 | 200 |
| GET | `/risk-tags` | 위험 태그 기준 목록 조회, 민원 분석과 별개 | 200 |

## 마스킹 응답 예시

분석 요청은 `/complaints/{complaintId}/risk-analysis`로 통일하며 기존 `/review` 경로는 제공하지 않는다.

서비스는 `risk.service`에 세 개만 둔다.

- `RiskAnalysisService`: 전체 분석 실행·저장·조회. 태그 병합과 점수 계산은 내부 private 메서드로 처리한다.
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

## 전체 분석 응답

```json
{
  "originalContent": "원본 민원",
  "riskyExpressionCount": 1,
  "riskyExpressions": [
    {"expression": "위험 표현", "reason": "판단 이유"}
  ],
  "revision": {
    "complaintId": 42,
    "revisionReason": "최종 위험 요소를 반영한 수정 이유",
    "aiRevision": "수정 제안 문장"
  },
  "riskAnalysis": {
    "analysisId": 10,
    "complaintId": 42,
    "tags": [
      {"code": "THREAT", "detected": true, "ruleDetected": true,
       "llmDetected": false, "confidence": null, "evidence": "마스킹된 근거"}
    ],
    "riskScore": 5,
    "riskLevel": "HIGH",
    "aiReason": "LLM 분석 설명"
  }
}
```

예시는 구조 설명용이다. 실제 tags에는 미탐지 항목을 포함한 모든 위험 코드가 들어간다. riskyExpressions와 aiReason은 LLM 탐지 단계의 결과이며, revisionReason은 최종 병합 결과에 대한 수정 이유다. 수정안 생성기는 마스킹된 원문과 최종 분석을 받는다.

GET 응답은 위의 riskAnalysis 객체다. 위험 표현·수정 이유·수정안도 분석 이력에 저장하며, 같은 내용 버전으로 POST 분석을 다시 요청하면 기존 전체 응답을 반환한다. GET 분석 조회는 위험도·태그·근거만 반환한다. 과거 ID 조회는 해당 분석 시점의 결과다. 처리 경로·반복 이력 가산·권장 조치는 아직 구현되지 않았다.

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
