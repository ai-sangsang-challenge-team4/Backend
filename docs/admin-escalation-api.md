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
| GET | `/admin/escalations/{escalationId}` | 공유 상세 조회 |
| POST | `/admin/escalations/{escalationId}/review` | `SHARED → UNDER_REVIEW` |
| POST | `/admin/escalations/{escalationId}/complete` | 결과 저장 후 `UNDER_REVIEW → COMPLETED` |

상세 조회 응답은 화면의 세 탭과 같은 구조다.

```json
{
  "escalation": {
    "escalationId": 1,
    "complaintId": 42,
    "escalationStatus": "SHARED",
    "version": 0
  },
  "shareContent": {
    "adminId": 3,
    "scopes": ["MASKING_MESSAGES"],
    "reason": "공동 검토 부탁드립니다.",
    "maskedMessages": [{
      "messageId": 11,
      "senderRole": "PARENT",
      "content": "[전화번호]로 연락해 주세요.",
      "masked": true
    }],
    "originalMessages": [],
    "mediationPackageIncluded": false
  },
  "riskAnalysis": {},
  "history": {}
}
```

- `shareContent`는 `ShareEscalationRequest`의 대상·범위·사유와 실제 공유 데이터를 반환한다.
- `ORIGINAL_MESSAGES`가 선택되지 않았다면 원문은 응답에 포함하지 않는다.
- `riskAnalysis`는 교사 위험 분석 탭과 같은 `TeacherRiskResponse` 구조다.
- `history`는 교사 처리 기록 탭과 같은 `TeacherHistoryResponse` 구조다. 관리자 공유,
  검토 시작, 처리 완료도 각각 `ESCALATION_SHARED`, `ESCALATION_REVIEW_STARTED`,
  `ESCALATION_COMPLETED` 이벤트로 함께 기록된다.

목록은 `status`, `riskLevel` 쿼리 파라미터로 필터링할 수 있다. 예:
`GET /admin/escalations?status=SHARED&riskLevel=EMERGENCY`.

검토 시작 요청:

```json
{"version": 0}
```

처리 완료 요청:

```json
{
  "version": 1,
  "result": "관리자 검토를 완료했습니다."
}
```

공유 건은 JPA `@Version`으로 낙관적 잠금을 사용한다. 클라이언트가 보낸 버전이 최신 버전과
다르거나 같은 버전의 동시 요청이 먼저 커밋되면 409 `ESCALATION_VERSION_CONFLICT`를 반환한다.
상태를 건너뛰거나 역행하면 409 `ESCALATION_STATE_INVALID`다. 미배정 관리자의 상세 조회는
403 `ESCALATION_ACCESS_DENIED`다.

## EMERGENCY 위험도 변경 승인

현재 위험도가 EMERGENCY이고 교사가 점수를 변경하려면, 같은 `riskAnalysisId`에 연결된 공유 건이
`COMPLETED`여야 한다. 관리자 화면에는 별도 승인/반려 선택이 없으므로 결과를 입력하고
`COMPLETED`로 전환한 행을 승인 완료로 본다. 승인 전의
`POST /teacher/complaints/{complaintId}/risk-reviews` 요청은 409
`ADMIN_APPROVAL_REQUIRED`이며 위험도 이력은 생성되지 않는다.

## 주요 오류

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 사유·결과·버전 등 요청값 누락 |
| 400 | `ESCALATION_REASON_REQUIRED` | 서비스 계층에서 빈 공유 사유 감지 |
| 403 | `ESCALATION_ACCESS_DENIED` | 미배정 관리자 또는 관리자 권한 없음 |
| 404 | `ADMIN_NOT_FOUND` | 지정 관리자 없음 또는 자동 배정할 관리자 없음 |
| 404 | `ESCALATION_NOT_FOUND` | 공유 건 없음 |
| 409 | `ESCALATION_NOT_ALLOWED` | EMERGENCY가 아니거나 다른 조건으로 이미 공유됨 |
| 409 | `ESCALATION_STATE_INVALID` | 상태 순서 위반 |
| 409 | `ESCALATION_VERSION_CONFLICT` | 동시 수정 또는 오래된 버전 |
| 409 | `ADMIN_APPROVAL_REQUIRED` | EMERGENCY 위험도 변경 승인 미완료 |

개발 프로필에는 `admin@test.com` / `Test1234!` 관리자가 생성된다.
