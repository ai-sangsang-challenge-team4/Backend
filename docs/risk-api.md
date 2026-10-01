# 민원 위험 분석 API

## 명세 변경

중간 LLM 결과를 따로 저장하는 모델이 없으므로 기존 `POST /complaints/{complaintId}/risk-tags` 단계는 `POST /complaints/{complaintId}/risk-analysis`에 통합한다. 클라이언트가 중간 태그나 점수를 전달하지 않는다. 서버가 저장된 원문을 기준으로 전체 분석을 수행한다.

모든 API는 Bearer 인증이 필요하며 민원 작성자만 접근할 수 있다. 현재 교사 조회 권한은 제공하지 않는다. POST 요청 본문은 없다.

| 메서드 | 경로 | 동작 | 성공 |
|---|---|---|---|
| POST | `/complaints/{complaintId}/mask` | 원문의 규칙 기반 개인정보 마스킹 결과 저장·반환 | 200 |
| POST | `/complaints/{complaintId}/risk-analysis` | 마스킹 → 룰·LLM 탐지 → 병합 → 점수 계산·저장 → 수정안 생성 | 200 |
| GET | `/risk-analyses/{analysisId}` | 저장된 분석의 점수·등급·태그·근거 조회 | 200 |
| POST | `/complaints/{complaintId}/review` | 기존 호환용 전체 분석 API, risk-analysis와 같은 처리 | 200 |
| GET | `/risk-tags` | 위험 태그 기준 목록 조회, 민원 분석과 별개 | 200 |

## 마스킹 응답 예시

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

GET 응답은 위의 riskAnalysis 객체다. 수정안은 현재 저장하지 않아 GET으로 조회하지 않는다. 재분석할 때마다 새로운 analysisId가 생성되며, 과거 ID 조회는 해당 분석 시점의 결과를 반환한다. 처리 경로·반복 이력 가산·권장 조치는 아직 구현되지 않아 명세에서 제외했다.

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
