# 관리자 공유 및 검토 API

EMERGENCY 민원을 담당 교사가 관리자에게 공유하고, 지정된 관리자가 검토를 완료하는 흐름이다.
화면의 `확인 대기`, `검토 중`, `처리 완료`는 각각 API 상태 `SHARED`,
`UNDER_REVIEW`, `COMPLETED`에 대응한다. 공유 확인 창을 닫거나 취소하면 API를 호출하지
않으므로 조회 상태는 `NOT_SHARED`로 유지된다.

## 교사 API

### 공유 상태 조회

`GET /teacher/complaints/{complaintId}/escalation`

공유 행이 없으면 200과 함께 `escalationStatus=NOT_SHARED`를 반환한다. 담당 교사만 조회할 수 있다.

### 관리자 공유

`POST /teacher/complaints/{complaintId}/escalation`

```json
{
  "adminId": 3,
  "scopes": ["MASKING_MESSAGES", "ORIGINAL_MESSAGES"],
  "reason": "공동 검토 부탁드립니다."
}
```

- `MASKING_MESSAGES`는 화면의 ‘개인정보가 제거된 메시지’이며 항상 포함된다.
- 선택 범위는 `ORIGINAL_MESSAGES`, `MEDIATION_PACKAGE`다.
- 화면처럼 관리자를 직접 선택하지 않는 경우 `adminId`를 생략할 수 있으며, 서버가 등록된
  관리자 중 첫 번째 관리자를 배정한다. 관리자가 한 명도 없으면 404다.
- 담당 교사의 현재 위험도가 EMERGENCY인 민원만 공유할 수 있다.
- 같은 대상·범위·사유로 중복 요청하면 기존 공유 건을 반환한다. 민원별 공유 행에는 DB 유일
  제약이 있고, 민원 쓰기 잠금으로 동시 클릭도 한 건만 생성된다.

성공 응답의 `escalationStatus`는 `SHARED`다. `riskAnalysisId`, `riskScore`, `riskLevel`은
공유 시점의 분석 결과이며, 이후 승인 검증도 이 분석 ID에 고정된다.

## 관리자 API

모든 경로는 ADMIN 권한이 필요하며, 배정받은 공유 건만 읽거나 변경할 수 있다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/admin/escalations` | 공유 목록 조회 |
| GET | `/admin/escalations/{escalationId}` | 상단 정보·처리 상태 조회 |
| GET | `/admin/escalations/{escalationId}/messages` | 공유 내용 탭 조회 |
| GET | `/admin/escalations/{escalationId}/risk` | 위험 분석 탭 조회 |
| GET | `/admin/escalations/{escalationId}/history` | 처리 기록 탭 조회 |
| POST | `/admin/escalations/{escalationId}/review` | `SHARED → UNDER_REVIEW` |
| POST | `/admin/escalations/{escalationId}/risk-change-decision` | EMERGENCY 위험도 변경 승인·거부 |
| POST | `/admin/escalations/{escalationId}/complete` | 결과 저장 후 `UNDER_REVIEW → COMPLETED` |

목록과 상단 상세 조회는 화면에 필요한 요약 정보만 반환한다.
`currentRiskLevel`은 관리자 승인으로 적용된 최신 교사 검토를 반영한다.

```json
{
  "escalationId": 1,
  "complaintId": 1,
  "sharedBy": "Demo Teacher",
  "reason": "공동 검토 부탁드립니다.",
  "currentRiskLevel": "HIGH",
  "escalationStatus": "UNDER_REVIEW",
  "sharedAt": "2026-10-27T14:18:31"
}
```

검토 시작, 위험도 변경 결정, 처리 완료 응답은 상태 확인에 필요한 값만 반환한다.

```json
{
  "escalationId": 1,
  "escalationStatus": "UNDER_REVIEW",
  "riskReviewStatus": "APPLIED",
  "currentRiskScore": 5,
  "currentRiskLevel": "HIGH"
}
```

교사 화면과 마찬가지로 상단 정보와 세 탭을 별도 API로 조회한다. 아래는
`GET /admin/escalations/{escalationId}/messages`의 공유 내용 응답 예시다.

```json
{
  "reason": "공동 검토 부탁드립니다.",
  "maskedMessages": [{
    "messageId": "message:11",
    "senderRole": "PARENT",
    "senderName": "Demo Parent",
    "content": "[전화번호]로 연락해 주세요.",
    "sentAt": "2026-10-27T14:10:00",
    "summarized": false
  }],
  "originalMessages": [],
  "mediationPackageIncluded": false
}
```

- `/messages`는 공유 사유와 선택된 범위의 메시지만 반환한다.
- 메시지 항목은 교사 대화 응답의 `TeacherConversationResponse.Message` 구조를 재사용한다.
- `MASKING_MESSAGES`가 선택되면 `maskedMessages`에 개인정보 마스킹 내용을 반환한다.
- `ORIGINAL_MESSAGES`가 선택되면 `originalMessages`에 원문을 반환하고, 선택하지 않으면 빈 배열이다.
- `MEDIATION_PACKAGE`가 선택되면 `mediationPackageIncluded=true`다. 현재는 별도 증빙 패키지 데이터 모델은 없다.
- `/risk`는 교사 위험 분석 탭과 같은 `TeacherRiskResponse` 구조다.
- `/history`는 교사 처리 기록 탭과 같은 `TeacherHistoryResponse` 구조다. 관리자 공유,
  검토 시작, 교사의 위험도 변경 요청, 관리자 승인·거부, 처리 완료도 각각 `ESCALATION_SHARED`,
  `ESCALATION_REVIEW_STARTED`, `ESCALATION_RISK_CHANGE_REQUESTED`,
  `ESCALATION_RISK_CHANGE_APPROVED`, `ESCALATION_RISK_CHANGE_REJECTED`,
  `ESCALATION_COMPLETED` 이벤트로 함께 기록된다.
- 처리 기록의 이벤트별로 해당하지 않는 nullable 필드는 JSON 응답에서 제외된다.

목록은 `status`, `riskLevel` 쿼리 파라미터로 필터링할 수 있다. 예:
`GET /admin/escalations?status=SHARED&riskLevel=EMERGENCY`.

검토 시작 요청:

```json
{"message": "학생 안전 조치를 먼저 확인합니다."}
```

`message`는 관리자가 해당 단계에서 남기는 메시지이며 필수이고 최대 500자다.

EMERGENCY 위험도 변경 승인 요청:

```json
{
  "status": "APPLIED",
  "message": "EMERGENCY 위험도 하향 조정을 승인합니다."
}
```

`POST /admin/escalations/{escalationId}/risk-change-decision`은 `UNDER_REVIEW` 상태에서만
호출할 수 있으며, 먼저 교사가 위험도 변경을 요청해야 한다. `status=APPLIED`이면 요청된
점수로 `RiskReview`를 생성하지만 `escalationStatus`는 변경하지 않는다.

EMERGENCY 위험도 변경 거부 요청:

```json
{
  "status": "REJECTED",
  "message": "현재 위험 요소를 고려하면 하향 조정할 수 없습니다."
}
```

`status=REJECTED`이면 실제 위험도와 `RiskReview`는 변경하지 않는다.
`PENDING_ADMIN_APPROVAL`은 교사 요청 시 서버가 설정하는 상태이므로 관리자 결정 요청값으로
사용할 수 없으며 409 `ESCALATION_STATE_INVALID`를 반환한다.

처리 완료 요청:

```json
{
  "message": "관리자 검토를 완료했습니다."
}
```

검토 시작과 처리 완료는 `EscalationMessageRequest`, 위험도 변경 승인·거부는
`RiskChangeDecisionRequest`를 사용하며 클라이언트는 version을 보내지 않는다.
공유 건은 JPA `@Version`으로 낙관적 잠금을 사용하므로 같은 행을 동시에 저장하면 서버에서
충돌을 감지하여 409 `ESCALATION_VERSION_CONFLICT`를 반환한다.
상태를 건너뛰거나 역행하면 409 `ESCALATION_STATE_INVALID`다. 미배정 관리자의 상세 조회는
403 `ESCALATION_ACCESS_DENIED`다.

## EMERGENCY 위험도 변경 승인

현재 위험도가 EMERGENCY인 경우 교사는 먼저
`POST /teacher/complaints/{complaintId}/risk-reviews`로 변경할 점수와 사유를 요청한다.
연결된 관리자 공유 건이 없으면 409 `ADMIN_APPROVAL_REQUIRED`지만, 공유 건이 있으면 실제
위험도는 변경하지 않고 `status=PENDING_ADMIN_APPROVAL`을 반환한다. 요청한 점수·등급·사유·
시각은 관리자가 승인하거나 거부할 때까지 `ComplaintEscalation`에 저장된다.
승인 대기 시점에는 `RiskReview`가 아직 생성되지 않으므로 `riskReviewId`는 JSON 응답에서
제외된다. 관리자가 `APPLIED`로 승인할 때 `RiskReview`가 생성된다.

관리자가 `risk-change-decision`에 `APPLIED`를 보내는 시점에 실제 `RiskReview`가 생성되어 위험도가
변경되고 `riskReviewStatus=APPLIED`가 된다. 관리자가 거부하면 `REJECTED`가 되고 실제
위험도는 EMERGENCY로 유지된다. 승인 대기 중에는 처리 완료할 수 없으며, 관리자가 승인이나
거부로 결정한 뒤에만 `COMPLETED`로 전환할 수 있다. 교사가 거부 후 다시 요청하면 상태는
`PENDING_ADMIN_APPROVAL`로 돌아간다.

## 주요 오류

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 사유 또는 관리자 메시지 누락 |
| 400 | `ESCALATION_REASON_REQUIRED` | 서비스 계층에서 빈 공유 사유 감지 |
| 403 | `ESCALATION_ACCESS_DENIED` | 미배정 관리자 또는 관리자 권한 없음 |
| 404 | `ADMIN_NOT_FOUND` | 지정 관리자 없음 또는 자동 배정할 관리자 없음 |
| 404 | `ESCALATION_NOT_FOUND` | 공유 건 없음 |
| 409 | `ESCALATION_NOT_ALLOWED` | EMERGENCY가 아니거나 다른 조건으로 이미 공유됨 |
| 409 | `ESCALATION_STATE_INVALID` | 상태 순서 위반 |
| 409 | `ESCALATION_VERSION_CONFLICT` | 서버에서 실제 동시 수정 충돌 감지 |
| 409 | `ADMIN_APPROVAL_REQUIRED` | EMERGENCY 위험도 변경 승인 미완료 |

## 개발 프로필 테스트 데이터

`dev` 프로필로 실행하면 아래 계정과 관리자 공유 테스트용 민원이 생성된다.

| 역할 | 계정 | 비밀번호 |
|---|---|---|
| 교사 | `teacher@test.com` | `Test1234!` |
| 관리자 | `admin@test.com` | `Test1234!` |

테스트 민원은 `OPEN`, 교사 응답 대기 상태이며 완료된 위험 분석 결과가
`riskScore=30`, `riskLevel=EMERGENCY`로 저장된다. `THREAT` 위험 요소에는 직접적인
신체 위해와 구체적인 보복 계획이 감지된 것으로 저장한다. 이는 긴급 판정 규칙을 실행한
결과가 아니라 관리자 공유 흐름을 확인하기 위한 고정 개발 데이터다.

서버 시작 로그의 `emergencyComplaintId`를 공유 API의 `{complaintId}`로 사용한다.
`adminId`는 함께 출력되지만 공유 요청에서 생략해도 자동으로 개발용 관리자가 배정된다.
