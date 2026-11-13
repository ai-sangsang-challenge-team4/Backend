# 민원 위험 분석 API

## 명세 변경

중간 LLM 결과를 따로 저장하는 모델이 없으므로 기존 `POST /complaints/{complaintId}/risk-tags` 단계는 `POST /complaints/{complaintId}/risk-analysis`에 통합한다. 클라이언트가 중간 태그나 점수를 전달하지 않는다. 서버가 저장된 원문을 기준으로 전체 분석을 수행한다.

모든 API는 Bearer 인증이 필요하며 민원 작성자만 접근할 수 있다. 현재 교사 조회 권한은 제공하지 않는다. POST 요청 본문은 없다.

| 메서드 | 경로 | 동작 | 성공 |
|---|---|---|---|
| POST | `/complaints/{complaintId}/mask` | 원문의 규칙 기반 개인정보 마스킹 결과 저장·반환 | 200 |
| POST | `/complaints/{complaintId}/risk-analysis` | 마스킹 → 룰·LLM 탐지 → 병합·점수 계산 → 수정안 검증 → 저장 | 200 |
| POST | `/complaints/{complaintId}/messages/{messageId}/risk-analysis` | 학부모 추가 문의 메시지의 위험 분석·수정안 생성 | 200 |
| GET | `/risk-analyses/{analysisId}` | 저장된 분석의 점수·등급 조회 | 200 |
| GET | `/risk-analyses/{analysisId}/findings` | 최종 감지 태그·근거·태그별 수정 제안·최종 수정본 조회 | 200 |
| GET | `/risk-analyses/{analysisId}/detector-results` | 전체 태그의 Rule·LLM 상태·Severity·반복 횟수·안전 신호 조회 | 200 |
| GET | `/risk-tags` | 위험 태그 기준 목록 조회, 민원 분석과 별개 | 200 |
| POST | `/teacher/complaints/{complaintId}/risk-reviews` | 교사가 1~7점 위험도를 검토하고, EMERGENCY 변경은 관리자 승인을 요청 | 201 |
| POST | `/teacher/complaints/{complaintId}/messages/{messageId}/original-views` | 담당 교사가 전송된 학부모 메시지 원문을 열람하고 감사 로그 저장 | 200 |
| GET | `/teacher/complaints/{complaintId}/history` | 통합 이력과 로그 누락 여부 조회 | 200 |
| POST | `/teacher/complaints/{complaintId}/emergency-review` | 공유 여부와 무관하게 EMERGENCY 하향 재검토 요청 | 201 |
| GET | `/teacher/complaints/{complaintId}/emergency-review/current` | 최근 재검토 요청과 안전 잠금 상태 조회 | 200 |
| GET | `/admin/emergency-review` | 지정 검토자에게 배정된 재검토 요청 목록 | 200 |
| GET | `/admin/emergency-review/{requestId}` | 지정 검토자의 재검토 요청 조회 | 200 |
| PATCH | `/admin/emergency-review/{requestId}` | 지정 검토자의 위험도 하향 승인·거부 및 안전 잠금 유지·해제 | 200 |
| GET | `/teacher/complaints/{complaintId}/response-templates` | High 민원의 최신 활성 제한형 템플릿 조회 | 200 |
| POST | `/teacher/complaints/{complaintId}/response-templates/{versionId}/adoptions` | 템플릿 변수 치환·교사 최종 편집본 채택 로그 저장 | 201 |
| POST | `/teacher/complaints/{complaintId}/escalation` | EMERGENCY 민원을 관리자에게 공유 | 201 |
| GET | `/admin/escalations` | 배정된 관리자 공유 건 조회 | 200 |

## 마스킹 응답 예시

분석 요청은 `/complaints/{complaintId}/risk-analysis`로 통일하며 기존 `/review` 경로는 제공하지 않는다.

분석 실행과 조회는 `risk.service`의 별도 서비스로 관리한다.

- `RiskAnalysisService`: 분석 실행·결과 및 수정안 저장. 태그 병합과 점수 계산은 RiskEvaluator로 처리한다.
- `RiskAnalysisQueryService`: 저장된 위험도 요약·근거 및 수정 제안·Rule/LLM 상세 조회. 읽기 전용 트랜잭션에서 완료 여부와 작성자 권한을 검증한다.
- `risk.masking.ComplaintMaskingService`: 마스킹 API의 민원 조회·권한/상태 검증·마스킹 결과 저장.
- `RiskTagService`: 위험 태그 기준 목록 조회.

룰 탐지기·LLM 분석기·수정안 생성기 인터페이스 및 `risk.masking`의 마스킹 구현은 별도로 유지한다.

## 외부 AI 서버 연동

Spring Boot와 FastAPI는 같은 EC2에서 실행하며, FastAPI는 외부에 공개하지 않고
`127.0.0.1:8000`에 바인딩한다. 백엔드 기본 주소는 `http://localhost:8000`이고 운영 환경에서
`AI_SERVER_URL`로 재정의할 수 있다. 연결 제한 시간과 요청 제한 시간은 각각
`AI_SERVER_CONNECT_TIMEOUT_MS`, `AI_SERVER_REQUEST_TIMEOUT_MS`로 설정한다.

| 백엔드 경계 | FastAPI 경로 | 입력 개인정보 정책 | 장애 정책 |
|---|---|---|---|
| `PythonLlmRiskAnalyzer` | `POST /api/v1/ai/analyze` | 마스킹 본문·마스킹 Rule 근거만 전달 | Rule-only 잠정 분석 |
| `PythonComplaintRevisionGenerator` | `POST /api/v1/ai/revision` | 마스킹 본문·마스킹 최종 태그만 전달 | 수정안 없이 Rule-only 잠정 분석 |
| `PythonTeacherSupportContentProvider` | `POST /api/v1/ai/summary` | 마스킹된 학부모 메시지만 전달 | 요약 `UNAVAILABLE`, 원문 자동 노출 금지 |
| `PythonTeacherSupportContentProvider` | `POST /api/v1/ai/response-draft` | 전체 대화를 메시지별 마스킹 후 전달 | 초안 미제공, 502/503 응답 |

공통 클라이언트는 timeout과 HTTP 상태를 일관되게 변환하고, 응답의 알 수 없는 필드와 숫자·불리언
문자열 coercion을 거부한다. 요청·응답 본문은 오류 메시지나 로그에 기록하지 않는다. FastAPI의
4xx는 백엔드 연동 구성 오류로 취급하고 429·5xx·연결 실패·timeout은 외부 AI 장애로 취급한다.

초기 Mock 검증용이던 백엔드 `POST /api/v1/ai/analyze`는 제품 민원 흐름과 중복되어 제거했다.
클라이언트는 `POST /complaints/{complaintId}/risk-analysis` 또는 메시지별 위험 분석 API를
사용하며, `/api/v1/ai/*`는 Spring Boot가 FastAPI에 호출하는 내부 경로다.

```json
{
  "complaintId": 42,
  "contentVersion": 1,
  "maskedContent": "[전화번호]로 연락 부탁드립니다."
}
```

전화번호(국내 휴대폰·일반 지역번호·050 가상번호·070 및 일부 +82 형태, `-`·`.`·공백 구분), 이메일, 주민등록번호 형태를 정규식으로 치환한다. 이름·자유 형식 주소·계좌번호 등 모든 개인정보를 탐지하는 기능은 아니다. 200은 지원 패턴의 치환 성공을 뜻하며 외부 AI 전송에 안전하다는 보장은 아니다. 불확실성을 판정하는 로직이 없으므로 기존 명세의 422는 현재 제공하지 않는다.

원문은 변경하지 않는다. 민원 수정 시 기존 maskedContent는 초기화된다. 전체 분석 API는 항상 현재 원문을 다시 마스킹하므로 mask API의 선행 호출은 선택 사항이다. 룰의 탐지 근거도 마스킹 후 저장·수정안 생성에 사용한다.
마스킹은 외부 AI보다 앞선 개인정보 가드레일이다. 제출 중에도 마스킹 자체가 실패하면 Rule과
LLM을 실행하지 않고 500 `PRIVACY_MASKING_FAILED`로 제출을 중단한다. 외부 AI의 타임아웃·연결 실패 또는 검증 불가능한
응답은 Rule-only 잠정 분석으로 전환한다. Rule, 반복 문맥, 점수 설정과 DB 처리 같은 백엔드
내부 오류는 fail-open하지 않는다.

## 분석 실행 응답

POST `/complaints/{complaintId}/risk-analysis`와 추가 문의 위험 분석 API는
`RiskSummaryResponse`를 반환한다.

```json
{
  "analysisId": 10,
  "complaintId": 42,
  "riskScore": 55,
  "riskLevel": "HIGH",
  "aiAnalysisStatus": "COMPLETED",
  "analysisMode": "HYBRID",
  "manualReviewRequired": false,
  "analysisMessage": "AI 위험 분석이 완료되었습니다."
}
```

일반 위험 분석과 추가 문의 위험 분석 모두 `aiAnalysisStatus`를 반환한다. 새 하이브리드 분석은
`COMPLETED`, 기존 하이브리드 결과는 `REUSED`, 기존 Rule-only 결과는 `REUSED_RULE_ONLY`,
외부 AI 장애는 `RULE_ONLY_AI_UNAVAILABLE`, 무효 응답은 `RULE_ONLY_INVALID_RESPONSE`다.
내부 가드레일 오류는 성공 응답 대신 단계별 HTTP 500 오류 코드를 반환하므로 이 필드가 없다.

시스템 위험 점수는 태그별 Rule 점수와 LLM 점수 중 큰 값을 합산해 최대 100점으로 제한한 값이다.
기본 태그 점수는
`PROFANITY 6`, `INSULT 10`, `THREAT 25`, `LEGAL 4`, `EXPOSURE 12`, `REPEAT 6`,
`UNFAIR_REQUEST 8`, `PRIVACY 15`다. 등급은
0~20 LOW, 21~40 MEDIUM, 41~60 HIGH, 61~100 EMERGENCY다.

신규 LLM 응답은 전체 8개 태그에 `severity`와 `repeatCount` 필드를 반환한다. 두 값은
`PRESENT=severity 1~3/repeatCount 0~10,000`, `ABSENT=0/0`,
`BOUNDARY/UNKNOWN=null/null`만 허용한다. LLM 점수는 서버의
`RiskScorePolicy`가 `defaultScore × severity`로 계산하고, confidence는 점수에 사용하지 않는다.
같은 태그의 최종 점수는 `max(Rule 점수, LLM 점수)`이므로 중복 합산하지 않는다. 신규 필드가
모두 없는 구형 응답은 기존 고정 점수(1배)로 처리하지만, 신규 필드를 일부만 보내거나 상태와
Severity가 일치하지 않으면 응답 전체를 무효로 처리한다.

| Severity | LLM 태그 점수 |
|---|---:|
| 0 | 0 |
| 1 | defaultScore |
| 2 | defaultScore × 2 |
| 3 | defaultScore × 3 |

`repeatCount`는 현재 민원 원문에서 확인한 위험 표현 횟수이며 점수에 직접 곱하지 않는다.
반복 횟수·강도·대상 특정 여부를 종합한 Severity를 통해 현재 문장 점수에 반영하고, 과거 민원
이력에 걸친 반복은 별도 `REPEAT` 태그로 평가한다. 이 분리는 반복 횟수와 REPEAT 태그의
이중 가산을 방지한다.

`safetyFlags`의 `credibleThreat`, `studentSafetyConcern`, `privacyExposure`는 각각
`true`, `false`, `null`을 허용한다. 하나라도 true이면 서버가 최종 점수를 100점
EMERGENCY로 승격해 기존 관리자 재검토와 답변 차단 경로에 연결한다. null은 정보 부족을
뜻하며 false로 치환하지 않는다. AI가 임의 점수나 가중치를 직접 전달하는 구조는 허용하지 않는다.

POST는 원문을 마스킹한 뒤 원문 Rule 탐지를 실행하고, Rule의 evidence와 reason을 다시
마스킹해 마스킹 본문과 함께 LLM에 전달한다. 결정론적 Rule PRESENT와 LLM PRESENT는 합집합으로
보존하고 태그별 점수는 두 출처의 최댓값만 사용한다. LLM이 누락한 태그는 Rule 상태로 fallback한다.
이후 점수 계산과
수정안 생성·저장을 수행한다.
LLM 출력이 스키마 검증에 실패하면 한 번 재생성한다. 재생성도 실패하거나 외부 AI가
연결·시간초과·429·5xx로 불가하면 Rule 결과만으로 점수와 등급을 계산·저장하고 HTTP 200으로
`analysisMode=RULE_ONLY`, `manualReviewRequired=true`를 반환한다. 이때 LLM 상태는 `UNKNOWN`,
confidence는 `null`이고 수정안은 생성하지 않는다.
위험 분석 LLM은 성공했더라도 수정안 생성 AI가 연결·응답 검증에 실패하면 아직 저장하지 않은
하이브리드 결과를 폐기하고 Rule-only 점수·등급만 한 번 저장한다. 잘못된 수정안을 저장하지 않으며
같은 수동 검토 안내와 `AI_ANALYSIS_RULE_FALLBACK` 감사 이벤트를 사용한다. 요청 직렬화·설정 같은
내부 연동 오류는 Rule-only로 전환하지 않고 fail-closed로 처리한다.
응답에는 요약만 포함하며, 근거와 태그별 수정 제안 및 전체 수정본은
`GET /risk-analyses/{analysisId}/findings`로 조회한다.
Rule·LLM 상세는 `GET /risk-analyses/{analysisId}/detector-results`로 조회한다.
같은 내용 버전으로 분석을 다시 요청하면 기존 분석의 요약을 반환하며 AI를 다시 호출하지 않는다.
과거 ID 조회는 해당 분석 시점의 결과다. `ComplaintReviewResponse`는 삭제했다.
처리 경로·반복 이력 가산·권장 조치는 아직 구현되지 않았다.

## 교사 위험도 검토

교사는 `OPEN`이며 교사 답변 차례인 담당 민원의 현재 위험도를 검토한다.

```json
{
  "riskScore": 5,
  "reason": "표현은 강하지만 즉각적인 위해 가능성은 낮다고 판단했습니다."
}
```

- 교사는 기존처럼 1~7을 선택한다. 서버는 선택값에 10을 곱해 10~70 위험 점수로 환산한다.
- `/risk` 응답의 `reviewScale`은 교사의 1~7 선택값이고, `reviewScore`는 환산된 10~70 점수다.
  실제 교사 검토가 없으면 두 필드와 `reviewLevel`은 null이다.
- `/risk` 응답의 `currentRiskScore`는 교사 검토가 있으면 `reviewScore`, 없으면 시스템
  `riskScore`다. 시스템 점수는 0~100이고 기존 교사 선택 척도의 환산 점수는 10~70이다.
- 최초 검토값은 시스템 등급을 LOW 2점, MEDIUM 4점, HIGH 6점, EMERGENCY 7점으로 변환한다.
- 현재 점수보다 낮추면 reason이 필수다.
- 현재 점수보다 높이거나 동일하게 확인하면 reason은 선택 사항이다.
- 현재 등급이 EMERGENCY인 상태에서 점수를 낮추면 관리자 공유 여부와 관계없이 별도의
  Emergency 재검토 요청을 생성하고 `PENDING_ADMIN_APPROVAL`, `safetyLock=true`를 반환한다.
  요청 사유는 필수이며 처리 중이거나 안전 잠금이 유지 중인 요청이 있으면
  409 `EMERGENCY_REVIEW_DUPLICATE`다.
- 요청 시 명시한 관리자, 기존 공유 담당 관리자, 기본 관리자 순으로 지정 검토자를 정한다.
  지정 검토자만 승인·거부하거나 안전 잠금을 변경할 수 있다.
- 지정 검토자가 승인하면 요청된 값으로 `risk_reviews`가 생성되고 현재 위험도에 반영된다.
  `safetyLock`은 Emergency 전용이므로 하향 승인을 `APPLIED`로 처리하는 같은 트랜잭션에서
  자동 해제한다. 거부 또는 승인 전에는 잠금을 유지한다.
- 기존 `POST /teacher/complaints/{complaintId}/risk-reviews`에서 EMERGENCY를 하향하는 경우도
  동일한 재검토 요청 흐름을 사용한다.
- 시스템 분석 결과는 덮어쓰지 않고 `risk_reviews`에 변경 전후 점수·등급과 교사,
  사유, 검토 시각을 누적한다.
- `GET /teacher/complaints/{complaintId}/risk`는 시스템 점수와 최신 검토가 반영된
  현재 점수를 함께 반환한다.
- `GET /teacher/complaints/{complaintId}/history`에는 `RISK_REVIEWED` 이벤트가 추가된다.

교사 검토 점수 구간은 1~2 LOW, 3~4 MEDIUM, 5~6 HIGH, 7 EMERGENCY다.

## 재분석 및 최종 제출

- 초안의 임시저장과 사용자의 직접 수정 횟수는 제한하지 않는다. 최초 민원과 학부모 추가 문의에 같은 정책을 적용한다.
- 사용자에게 수정안을 보여주는 AI 검토는 학부모 메시지별 1회만 수행한다.
- AI 검토 후 내용이 변경됐다면 제출 시 최종 내용의 위험도를 내부적으로 한 번 더 분석한다. 이 분석은 새 수정안을 생성하지 않는다.
- 내용이 변경되지 않았다면 기존 검토 결과를 재사용한다. 따라서 학부모 메시지별 성공한 AI 분석은 최대 2회다.
- 기존 Rule-only 분석을 재사용한 제출은 `REUSED_RULE_ONLY`와 수동 검토 안내를 반환한다.
  하이브리드 분석 재사용은 기존처럼 `REUSED`다.
- 분석과 제출 처리 중에는 DRAFT를 유지한다. 권한·상태·입력·마스킹·Rule·점수·저장 오류는
  제출을 롤백한다. 외부 LLM의 타임아웃·연결 실패는 즉시, 스키마 검증 실패는 1회 재생성 후
  Rule-only 잠정 분석으로 전환하므로 원문 전송을 막지 않는다.
- 동일 내용 저장은 contentVersion을 올리지 않는다. 같은 버전 분석 재요청은 저장 결과를 반환하고 AI 호출/횟수를 추가하지 않는다.
- PATCH 후 바로 POST send를 호출한다. 서버는 최종 문장이 분석 원문과 같으면 재사용하고, 다르면 한 번 재분석한다. 수정했다가 원문으로 되돌린 경우도 재사용한다.
- 수정 후 POST risk-analysis 호출은 409 FINAL_ANALYSIS_ON_SUBMIT. 최종 재분석은 send 내부에서만 실행하고 새 수정안을 생성하지 않는다. 최대 성공 분석 2회 제한은 유지한다.
- POST `/complaints/{complaintId}/send`: Bearer 인증 필요, 본문 없음. 최초 분석이 없어도 서버가
  최종 분석을 시도한다. 외부 LLM 실패는 Rule-only 점수·등급과 `RULE_ONLY_AI_UNAVAILABLE` 또는
  `RULE_ONLY_INVALID_RESPONSE`를 반환하면서 제출한다. 분석 횟수 제한은 `SKIPPED_LIMIT`를 반환한다.
- 최초 민원 제출 성공 시 200이며 상태 및 이력은 DRAFT → OPEN, waitingFor는 TEACHER다.
- 분석·수정·제출은 동일 민원 행의 비관적 쓰기 잠금으로 동시 실행을 직렬화한다. 현재 동기 처리이므로 AI 호출 동안 잠금이 유지된다.

DB에는 risk_analyses의 content_hash/completed/revision_reason/ai_revision 컬럼과 risk_analysis_expressions 테이블이 추가된다. 원문은 complaint_messages.content에만 보관하고, risk_analyses에는 동일 내용 분석을 재사용하기 위한 SHA-256 지문만 저장한다. 기존 분석에는 전체 재사용 응답이 없으므로 completed 기본값은 false이며 재사용·완료 횟수에서 제외된다. 기존 데이터를 유지하는 배포에서는 이 정책을 고려해 스키마를 반영해야 한다.

## 오류

| 상태 | 조건 |
|---|---|
| 400 | 마스킹/분석할 원문이 비어 있음, 경로 ID 형식 오류 |
| 401 | 인증되지 않았거나 인증 사용자 정보가 없음 |
| 403 | 다른 작성자의 민원·분석 접근 |
| 404 | 민원 또는 분석이 없음 |
| 409 | DRAFT가 아닌 민원에 마스킹/분석 요청 |
| 500 | `PRIVACY_MASKING_FAILED`: 개인정보 마스킹 실패 |
| 500 | `RISK_RULE_PROCESSING_FAILED`: Rule·반복 문맥 처리 실패 |
| 500 | `RISK_SCORE_CALCULATION_FAILED`: 태그 기준 조회·점수/등급 계산 실패 |
| 500 | `RISK_ANALYSIS_SAVE_FAILED`: 분석·태그·감사 이벤트 저장 실패 |
| 502 | Rule-only 정책이 없는 일반 AI API에서 외부 AI 응답 검증 실패 |
| 503 | Rule-only 정책이 없는 일반 AI API에서 외부 AI 연결·타임아웃·429·5xx 장애 |

위험 분석 API에서는 외부 AI의 502/503 원인을 Rule-only HTTP 200 응답으로 변환한다. 마스킹,
Rule, 점수 계산, 저장 등 내부 가드레일 실패는 변환하지 않고 위 단계별 코드와 HTTP 500을
반환한다. 이 실패 응답에는 분석 상태나 점수 결과가 없으며 트랜잭션은 롤백된다.

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
| MessageNotEditableException | 409 | MESSAGE_NOT_EDITABLE |
| MessageDraftNotFoundException | 404 | MESSAGE_DRAFT_NOT_FOUND |
| RiskAnalysisProcessingException(PRIVACY_MASKING) | 500 | PRIVACY_MASKING_FAILED |
| RiskAnalysisProcessingException(RULE_PROCESSING) | 500 | RISK_RULE_PROCESSING_FAILED |
| RiskAnalysisProcessingException(SCORE_CALCULATION) | 500 | RISK_SCORE_CALCULATION_FAILED |
| RiskAnalysisProcessingException(ANALYSIS_SAVE) | 500 | RISK_ANALYSIS_SAVE_FAILED |

```json
{"code":"COMPLAINT_ACCESS_DENIED","message":"해당 민원에 접근할 권한이 없습니다.","timestamp":"2026-10-01T09:00:00","path":"/complaints/42/mask"}
```

마스킹과 Rule 탐지는 실제 백엔드 규칙 처리다. Rule 탐지기는 DB에 적재한
`teachercare_rule_dictionary_v1.1_extended.csv` 72행을 메모리에 캐시해 사용한다.
CSV의 `risk_tag_code`는 백엔드 `RiskTagCode`와 동일한
`PROFANITY`, `INSULT`, `THREAT`, `LEGAL`, `EXPOSURE`, `REPEAT`,
`UNFAIR_REQUEST`, `PRIVACY`만 허용한다. Python AI 요청과 응답의 태그 코드도 같은 값을 사용한다.
일반 매칭은 `BOUNDARY`, `HINT_ONLY`는 `UNKNOWN` 후보로 LLM에 전달하며 키워드 매칭만으로
`PRESENT`를 확정하지 않는다. 실제 문맥 판단과 수정안 품질은 외부 LLM 구현과 평가가 필요하다.
외부 LLM 연동 전에 이름 등 미지원 개인정보 처리 정책을 별도로 정해야 한다.

`REPEAT` 문맥 판단을 위해 Python AI 요청에는 `repeatContext`를 함께 보낸다. 같은
`complaintId`의 최근 7일 전송 완료 이력에서 학부모 메시지 수, 교사 답변 수, 현재 처리
상태를 계산하고 각 메시지 본문을 마스킹한다. 현재 분석 중인 학부모 메시지를 포함해 학부모
메시지가 3건 이상이면 `repeatThresholdMet=true`로 계산하고 Rule의 `REPEAT` 후보를
`PRESENT`로 LLM에 전달한다. 교사 답변은 3건 기준에서 제외하지만 문맥 이력에는 포함한다.
LLM이 `REPEAT` 상태를 반환하면 현재 입력의 반복 횟수와 과거 이력 문맥을 함께 평가한다.
과거 이력 임계치를 만족한 결정론적 Rule PRESENT는 LLM이 낮출 수 없으며, LLM이 해당 태그를
생략한 경우에도 Rule 상태를 사용한다. 과거 이력이 없으면 LLM은 REPEAT를 UNKNOWN으로 반환한다.

현재 분석 메시지와 임시저장은 과거 이력 목록에서는 제외한다. 전체 과거 이력의 횟수는
보존하지만 본문은 최신 20개, 합계 8,000자까지만 선택한 뒤 시간순으로 전달한다. 제한이
적용되면 `historyTruncated=true`이며 개별 본문이 잘린 경우 `contentTruncated=true`다.
기간·판정 건수·메시지 수·글자 수는 각각 `risk.repeat.window-days`,
`risk.repeat.threshold`, `risk.repeat.max-messages`, `risk.repeat.max-characters`로 운영
환경에서 조정한다. 기본값은 7일·3건·20개·8,000자다. 다른 민원의 이력은 이번 정책 범위에
포함하지 않는다.

## 분리된 조회 응답

`RiskAnalysisSummaryResponse`는 analysisId, complaintId, riskScore, riskLevel만 반환한다.
내부 수정안 생성 입력은 `ComplaintRevisionGenerator.RiskEvaluation`으로 분리했다. POST 응답도 이 요약 DTO다.
수정안 생성 결과는 내부 ComplaintRevisionGenerator.RevisionResult로 받는다.
전체 수정본과 태그별 제안은 내부 수정 결과 타입으로 생성해 DB에 저장한 뒤 `ComplaintRiskReviewResponse`로 변환하여 반환한다.
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
Rule 탐지기는 실제 CSV 사전을 사용한다. LLM 문맥 판단과 수정안 생성 품질은 외부 AI 연동과
골든셋 평가가 필요하다.

detector-results는 results(code, finalState, rule, llm), llm(modelName, temperature),
safetyFlags를 반환한다.
상태는 `PRESENT`, `ABSENT`, `BOUNDARY`, `UNKNOWN` 중 하나다. rule/llm에는 state,
confidence, severity, repeatCount, evidenceSpans가 있고 각 span은 text, start, end를 반환한다.
Rule의 severity/repeatCount는 null이다. 위치를 확정할 수 없는
근거는 start/end가 null이다. 근거 없이 PRESENT만 반환된 결과는 BOUNDARY로 낮추며,
이력 정보가 없는 REPEAT는 UNKNOWN으로 보존한다. 규칙 탐지 confidence는 null이다.
모든 위험 코드(미감지 포함)를 반환한다. 기존 소유권 검증과 401/403/404 정책은 세 조회 모두 적용한다.
미완료 분석은 조회하지 않는다.

### 스키마 변경

- risk_tag_revision_suggestions: id, risk_analysis_id(FK), code, original_expression,
  suggested_expression, reason, suggestion_order. RiskAnalysis의 cascade/orphanRemoval로 함께 관리한다.
- complaint_risk_tags: rule_state, llm_state, final_state와 기존 호환용 탐지 boolean을 함께 유지하고,
  LLM의 severity, repeat_count를 nullable로 저장한다. null은 구형 응답 또는
  Rule-only 분석을 뜻한다.
- complaint_risk_evidence_spans, complaint_risk_rule_evidence_spans,
  complaint_risk_llm_evidence_spans: 최종·Rule·LLM 근거의 text/start/end를 저장한다.
  기존 evidence는 분석 당시 병합 근거로 유지한다. 기존 confidence는 LLM 신뢰도다.
- risk_analyses의 revision_reason/ai_revision은 전체 수정본으로 유지한다.
- risk_analyses의 credible_threat, student_safety_concern, privacy_exposure에 tri-state 안전 신호를
  저장한다. `V20261112_01__add_llm_severity_and_safety_flags.sql`은 이 컬럼과 범위 제약을 추가한다.

개발·테스트 환경은 H2와 `ddl-auto=create-drop`을 유지하며 Flyway는 비활성화되어 있다.
운영용 증분 변경은 `V20261112_01__add_llm_severity_and_safety_flags.sql`로 제공한다.
다만 저장소에 전체 초기 스키마 migration이 아직 없으므로 운영 적용 전 기존 DB를 Flyway
baseline으로 등록해야 한다. 신규 컬럼은 nullable이며 기존 분석은 백필하지 않고 구형 응답으로
해석한다. 롤백 시 애플리케이션을 이전 버전으로 되돌린 뒤 신규 컬럼과 제약을 제거할 수 있지만,
그 기간에 저장된 Severity와 안전 신호 감사 정보는 소실되므로 먼저 별도 보관해야 한다.

### 배포 확인과 롤백 조건

- 배포 후 신규/구형 응답별 성공률, `RULE_ONLY_INVALID_RESPONSE` 비율, 태그별 Severity 분포,
  safety flag별 true/null 비율, EMERGENCY 승격률, 교사·관리자 override 비율을 확인한다.
- AI 요청에는 마스킹된 본문만 포함되는지 샘플이 아닌 자동 지표와 테스트로 확인한다.
- 스키마 무효 폴백이 기준선보다 급증하거나, 정당한 신고·법적 대응의 오탐이 증가하거나,
  THREAT/PRIVACY 중요 사례가 한 단계 이상 과소평가되면 이전 애플리케이션 버전으로 롤백한다.
- 점수 정책 롤백 시 신규 nullable 컬럼은 즉시 삭제하지 않아도 구버전이 무시할 수 있다.
  컬럼 제거는 감사 데이터 보관 후 별도 배포로 수행한다.

### 현재 검증 상태

- 고정 fake 기반 단위·통합 테스트와 로컬 HTTP 계약 테스트는 신규 8태그 응답, 구형 응답 호환,
  Severity 경계, 중복 점수 방지, 안전 신호, 정당한 법적 대응, 개인정보 마스킹 순서를 검증한다.
- 저장소에는 비식별 골든셋과 평가 runner가 아직 없어 실제 모델 기준선 비교는 실행할 수 없다.
- AWS의 `teacherhub-ai.service` 실제 엔드포인트 주소/접속 설정은 저장소에 없으므로 배포 서버와의
  smoke test는 운영 환경에서 별도 수행해야 한다.

### 보조 DTO 파일 정리

API 응답 구조를 유지하면서 보조 타입을 사용하는 클래스 안으로 통합했다.

- RiskEvaluation → ComplaintRevisionGenerator.RiskEvaluation
- FinalRiskTagResponse → RiskEvaluator.FinalRiskTagResponse
- TagRevisionSuggestion → ComplaintRevisionGenerator.TagRevisionSuggestion

위 세 타입의 별도 DTO 파일은 삭제했다. 기본 위험도·근거·탐지기 상세 응답 DTO는 각각 유지한다.

## 민원 수정·제출 응답 및 상태 조회

DTO를 반환하는 기존 API의 응답은 유지한다. 빈 성공 응답을 반환하던 민원 수정·제출 API는 JSON 메시지를 반환한다.

- GET `/complaints/recipients?studentId={studentId}`: 학생의 최신 학급에 배정된 문의 가능 교사 목록을 반환한다.
- POST `/complaints`: `ComplaintCreateRequest`(`studentId`, `teacherId`, `title`, `content`)로 최초 초안을 생성한다. 선택한 교사는 해당 학생의 담당 교사여야 한다.
- PATCH `/complaints/{complaintId}`: 200, `{"message":"민원 내용이 수정되었습니다."}`.
  기존 204에서 200으로 변경했다. 요청 본문은 `ComplaintDraftUpdateRequest`(`content`)를 사용한다.
- POST `/complaints/{complaintId}/send`: 200, 제출 상태와 `submissionAnalysisStatus`,
  `aiAnalysisMessage`, `riskAnalysis` 요약을 반환한다. Rule-only이면 이 중첩 객체의 점수·등급과
  바깥의 `submissionAnalysisStatus`와 동일한 값인 `riskAnalysis.aiAnalysisStatus`,
  `analysisMode=RULE_ONLY`, `manualReviewRequired=true`로 프론트가 잠정 결과를 표시한다.
  `SKIPPED_LIMIT`처럼 분석 결과가 없으면 `riskAnalysis=null`이다.
- 추가 문의 전송 응답도 `submissionAnalysisStatus`, `aiAnalysisMessage`, `riskAnalysis`를
  반환해 최초 민원과 동일하게 처리한다.
  요청 본문은 없다.
- GET `/complaints/{complaintId}/status`: 200, 기존 ComplaintResponse 사용.

```json
{"complaintId":42,"status":"OPEN"}
```

상태 조회는 작성자만 가능하다. 인증 없음은 401, 다른 작성자의 민원은 403,
없는 민원은 404다. 조회만 수행하며 분석이나 수정안을 생성하지 않는다.
Postman에서는 생성 후 DRAFT, send 성공 후 OPEN을 조회하고,
제출 이후 PATCH가 409 COMPLAINT_NOT_DRAFT로 차단되는지 확인한다.

## 민원 목록 검색·필터·페이지네이션

학부모 `GET /complaints`와 교사 `GET /teacher/complaints`는 동일한 조회 조건을 사용한다.

| 쿼리 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | `0` | 0부터 시작하는 페이지 번호 |
| `keyword` | 없음 | 제목·학생명·상대방 이름·메시지 내용 통합 검색 |
| `status` | 없음 | `DRAFT`, `OPEN`, `CLOSED` |
| `riskLevel` | 없음 | `LOW`, `MEDIUM`, `HIGH`, `EMERGENCY` |
| `sort` | `LATEST` | `LATEST`(최근 활동순), `OLDEST`(오래된순) |

페이지 크기는 10개로 고정하며 클라이언트가 `size`를 변경할 수 없다. 교사 목록과 검색에서는 제출되지 않은 민원과 DRAFT 메시지를 제외한다. `riskLevel`은 최신 완료 분석에 최신 교사 검토가 있으면 그 등급을 기준으로 필터링한다.
목록의 `aiSummary`는 전체 민원의 핵심 내용과 현재 요청을 표시하는 일반 요약용 필드다.
FastAPI에는 현재 교사용 감정 완충 요약만 있고 일반 민원 요약 계약은 없으므로 이번 연동에서는
`null`을 반환한다. 목록 조회는 완충 요약 API를 호출하지 않는다. 일반 요약 API와 생성·저장
시점이 합의되면 메시지별 완충 요약과 별도 경계 및 저장 구조로 연동한다.

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "hasNext": false,
  "hasPrevious": false
}
```

## 민원 대화 메시지 흐름

민원 상태와 메시지 상태를 분리한다. 민원은 `DRAFT → OPEN → CLOSED`, 메시지는
`DRAFT → SENT → READ` 흐름이며 `waitingFor`가 현재 작성 차례를 나타낸다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/complaints/{complaintId}/messages` | 학부모용 전송 메시지 목록과 현재 차례 조회 |
| PUT | `/complaints/{complaintId}/message-draft` | 학부모 추가 문의 초안 생성 또는 버전 조건부 수정 |
| GET | `/complaints/{complaintId}/message-draft` | 학부모 추가 문의 임시저장 조회 |
| POST | `/complaints/{complaintId}/messages/{messageId}/risk-analysis` | 추가 문의 AI 위험 분석·수정 제안 생성 |
| POST | `/complaints/{complaintId}/messages/{messageId}/send` | 버전 조건 확인 후 추가 문의 전송 및 교사 차례로 변경 |
| POST | `/teacher/complaints/{complaintId}/close` | 교사가 답변 후 상담 완료 처리 |

교사 답변 API 경로는 기존 값을 유지한다. AI 초안 생성 시 지금까지 전송된 전체 대화가
입력 문맥이 되며 각 메시지는 개인정보 마스킹 후 `TeacherSupportContentProvider` 경계에
전달한다. 생성 결과는 빈값·최대 1,500자·모델명 형식을 검증하고 다시 마스킹한 뒤 반환한다.
임시저장과 최종 전송은 `complaint_messages`의 `TEACHER_REPLY` 행을
사용한다. 교사 전송 후 민원은 계속 OPEN이고 `waitingFor=PARENT`가 된다. 따라서 학부모가
추가 문의를 보내면 다시 `waitingFor=TEACHER`로 바뀌어 같은 분기 처리를 반복할 수 있다.
답변 전송만으로 자동 완료되지 않으며, 교사가 별도 완료 API를 호출해야 CLOSED가 된다.
완료된 문의에 학부모가 같은 사안의 추가 질문을 보내면 기존 문의를 OPEN으로 다시 열고
`waitingFor=TEACHER`로 변경하며 `CLOSED → OPEN` 상태 변경 이력을 남긴다. 다른 주제는 새 문의로 생성한다.

추가 문의 최초 저장은 `{"content":"..."}`처럼 버전을 생략할 수 있다. 기존 초안 수정은
`{"content":"...","expectedContentVersion":2}`, 전송은 `{"expectedContentVersion":2}`를
보낸다. 서버는 민원 행 쓰기 잠금 안에서 현재 버전 검사와 저장·전송을 연속 처리한다.
버전이 누락되거나 다르면 쓰기·전송 없이 409 `CONTENT_VERSION_CONFLICT`를 반환한다.

AI 초안은 영속화하지 않는다. `POST /teacher/complaints/{complaintId}/response/ai-draft`는
`complaintId`, `content`, `modelName`, `generatedAt`만 반환한다. 교사 답변 임시저장 및 제출
요청은 `{"content":"수정한 최종 답변"}`만 전달하며 `aiDraftId`는 사용하지 않는다.
히스토리에는 초안 내용 대신 `AI_DRAFT_GENERATED` 이벤트만 저장한다.

최종 제출 `POST /teacher/complaints/{complaintId}/responses`에는 최대 100자의
`Idempotency-Key` 헤더가 필수다. 동일 교사가 같은 키와 동일 내용으로 재요청하면 최초 전송
결과를 반환하며 메시지와 `MESSAGE_SENT` 이벤트를 중복 생성하지 않는다.

Low·Medium에서만 생성형 AI 초안을 허용한다. High는 생성형 AI 초안을 차단하고 검증된 제한형
공식 템플릿을 채택한 내용만 임시저장·전송할 수 있다. Emergency 또는 safetyLock 상태에서는
AI 초안 생성, 임시저장, 최종 전송과 상담 완료를 모두 차단한다. 정책 차단은
`AI_DRAFT_BLOCKED_BY_RISK_POLICY`, `RESPONSE_BLOCKED_BY_RISK_POLICY`,
`RESPONSE_BLOCKED_BY_SAFETY_LOCK` 감사 이벤트로 구분한다.
`GET /teacher/complaints/{complaintId}/risk`의 `analysisMode`, `manualReviewRequired`,
`analysisMessage`로 프론트가 잠정 분석 배지와 직접 검토 안내를 표시할 수 있다.

`GET /teacher/complaints/{complaintId}/messages`은 전송된 모든 메시지를 반환한다.
모든 위험도의 분석 완료 학부모 메시지는 완충 요약을 반환한다. 완충 요약 생성기에도 마스킹된
입력만 전달하고 출력은 다시 마스킹한다.
최초 민원과 후속 문의에 동일한 규칙을 적용한다. 교사 메시지는 원문을
반환하고 임시저장 DRAFT는 대화 내역에서 제외한다. 완충 요약 생성이 실패하거나 빈 결과를
반환하면 원문을 자동 노출하지 않고 안내 문구, `summarized=false`,
`summaryStatus=UNAVAILABLE`을 반환한다. 원문은 별도 열람 API와 `ORIGINAL_VIEWED` 감사 이벤트를
통해서만 제공한다.

`GET /teacher/complaints/{complaintId}/history`는 `ComplaintEventType`과 함께
`complaint_events`에 저장된 통합 이력을 발생 시각 역순으로 반환한다. 메시지 최초 읽음은
`MESSAGE_READ`로 저장한다. 현재 메시지·민원 상태, 완료 분석, 위험도 검토, 관리자 공유와
이벤트를 대조하며 누락이 있으면 `logComplete=false`와 `missingLogs`를 반환한다. 기존
`complaint_status_history`, `complaint_original_view_logs` 테이블은 사용하지 않는다.
