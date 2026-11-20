# PR 제목

feat: 교사 답변 완료와 학부모 최종 종료 흐름 분리

## 관련 이슈

- 별도 이슈 없음
- 논의 사항: 교사의 답변 완료와 민원의 최종 종료를 분리하고, 학부모 무응답 시 3일 후 자동 종료

## 변경 목적

기존에는 교사가 답변을 전송한 뒤 별도 종료 API를 호출해야 민원이 완료되어, 교사의 실제 답변 업무가 끝났는데도 미처리 상태가 계속 유지될 수 있었습니다.

교사 답변 완료와 민원 최종 종료를 분리하여 다음 목표를 달성합니다.

- 교사가 답변을 전송하면 즉시 미처리 목록에서 제외
- 학부모에게 문의 완료 또는 추가 문의 선택권 제공
- 학부모가 응답하지 않으면 교사 답변 후 3일 뒤 자동 종료
- 추가 문의는 기존 민원과 연결하여 전체 이력 유지

## 주요 변경 사항

### 상태 전이

민원 상태에 `ANSWERED`를 추가했습니다.

```text
DRAFT
  └─ 학부모 최초 전송 → OPEN (waitingFor=TEACHER)
       └─ 교사 답변 전송 → ANSWERED (waitingFor=PARENT)
            ├─ 학부모 문의 완료 → CLOSED (waitingFor=NONE)
            ├─ 학부모 추가 문의 → OPEN (waitingFor=TEACHER)
            └─ 3일간 무응답 → CLOSED (waitingFor=NONE)

CLOSED
  └─ 같은 사안의 추가 문의 → OPEN (waitingFor=TEACHER)
```

- 교사 답변 전송 시 `OPEN → ANSWERED` 상태 변경 이벤트를 기록합니다.
- 학부모 추가 문의 전송 시 `ANSWERED → OPEN` 또는 `CLOSED → OPEN` 이력을 기록합니다.
- 자동 종료는 `SYSTEM` 주체의 `ANSWERED → CLOSED` 감사 이벤트로 기록합니다.

### 학부모 문의 완료

- `POST /complaints/{complaintId}/close` API를 추가했습니다.
- 해당 민원의 학부모 작성자만 호출할 수 있습니다.
- `ANSWERED + waitingFor=PARENT` 상태에서만 종료할 수 있습니다.
- 성공 시 최신 교사 답변을 읽음 처리하고 `MESSAGE_READ` 이벤트를 기록합니다.
- 잘못된 상태에서 호출하면 `409 COMPLAINT_NOT_CLOSABLE`을 반환합니다.
- 중복 종료 요청은 `409 COMPLAINT_NOT_CLOSABLE`로 거절합니다.

응답 예시:

```json
{
  "complaintId": 42,
  "status": "CLOSED",
  "waitingFor": "NONE"
}
```

### 교사 답변 완료

- `POST /teacher/complaints/{complaintId}/responses` 성공 후 응답 상태가 다음과 같이 변경됩니다.

```json
{
  "complaintStatus": "ANSWERED",
  "waitingFor": "PARENT"
}
```

- `OPEN` 필터가 교사의 답변 대기 민원만 나타내므로 `ANSWERED` 민원은 교사 미처리 목록에서 제외됩니다.
- 기존 `POST /teacher/complaints/{complaintId}/close` API를 제거했습니다.

### 3일 무응답 자동 종료

- 교사 답변 시각인 `answeredAt`을 기준으로 정확히 3일이 지난 민원을 자동 종료합니다.
- 기본 실행 주기는 1시간이며 한 번에 100건씩 처리합니다.
- 후보 조회 후 민원 행에 비관적 잠금을 획득하고 상태와 시각을 다시 확인하여 학부모 추가 문의·종료 요청과의 경합을 보호합니다.
- 자동 종료 건수만 로그로 남기며 민원 내용이나 개인정보는 기록하지 않습니다.

운영 설정:

| 환경 변수 | 기본값 | 설명 |
|---|---:|---|
| `COMPLAINT_AUTO_CLOSE_ENABLED` | `true` | 자동 종료 활성화 여부 |
| `COMPLAINT_AUTO_CLOSE_AFTER_DAYS` | `3` | 교사 답변 후 자동 종료까지의 일수 |
| `COMPLAINT_AUTO_CLOSE_BATCH_SIZE` | `100` | 한 트랜잭션의 최대 처리 건수 |
| `COMPLAINT_AUTO_CLOSE_INITIAL_DELAY_MS` | `60000` | 애플리케이션 시작 후 최초 실행 지연 |
| `COMPLAINT_AUTO_CLOSE_INTERVAL_MS` | `3600000` | 자동 종료 실행 간격 |

## 정책·API·DB 변화

### 정책 변화

- 교사의 책임은 답변 전송 시점에 완료됩니다.
- 민원의 최종 종료 주체는 학부모 또는 무응답 자동 종료 시스템입니다.
- 학부모는 `ANSWERED`와 `CLOSED` 상태 모두에서 같은 사안의 추가 문의를 전송할 수 있습니다.
- 자동 종료는 LLM이나 위험 분석 결과에 의존하지 않는 결정론적 서버 정책입니다.

### API 변화

| 구분 | 메서드·경로 | 변화 |
|---|---|---|
| 변경 | `POST /teacher/complaints/{complaintId}/responses` | 성공 후 `ANSWERED`, `waitingFor=PARENT` 반환 |
| 추가 | `POST /complaints/{complaintId}/close` | 학부모 문의 완료 처리 |
| 제거 | `POST /teacher/complaints/{complaintId}/close` | 교사의 민원 종료 권한 제거 |
| 변경 | 민원 목록의 `status` 필터 | `ANSWERED` 값 추가 |

### DB 변화

Flyway `V5__separate_answered_and_closed_complaints.sql`을 추가했습니다.

- `complaints.status`에 `ANSWERED` 추가
- `complaints.answered_at` 추가
- 자동 종료 후보 조회 인덱스 `(status, answered_at, id)` 추가
- `complaint_events.previous_status`, `new_status`에 `ANSWERED` 추가
- 기존 `OPEN + waiting_for=PARENT` 데이터를 `ANSWERED`로 백필
- 백필 대상에 `OPEN → ANSWERED` 감사 이벤트 추가
- 기존 `CLOSED` 데이터는 레거시 이력 호환을 위해 그대로 유지

## 권한·동시성·안전 검토

- 학부모 종료 API는 소유자 검증 후에만 상태를 변경합니다.
- 학부모 종료, 추가 문의 전송, 자동 종료는 민원 행 잠금으로 직렬화합니다.
- 자동 종료는 잠금 획득 후 `ANSWERED`, `waitingFor=PARENT`, `answeredAt <= cutoff`을 다시 확인합니다.
- 민원 원문, 인증 정보, 개인정보는 자동 종료 로그에 포함하지 않습니다.
- 위험 분석, 개인정보 마스킹, Emergency 안전 잠금 정책은 변경하지 않았습니다.
- 프롬프트·모델·위험 판정 변경이 없어 AI 골든셋 평가는 대상이 아닙니다.

## 테스트 결과

```powershell
.\gradlew.bat test
```

- 결과: `BUILD SUCCESSFUL`
- 전체 218개 테스트 통과
- 검증 범위:
  - `OPEN → ANSWERED → CLOSED` 상태 전이
  - 3일 자동 종료 경계값
  - 학부모 소유자 권한과 잘못된 상태 거절
  - 자동 종료와 동시 상태 변경 재검증
  - `ANSWERED` 및 `CLOSED` 상태의 추가 문의 재개
  - 학부모 종료 시 최신 교사 답변 읽음 처리
  - 교사 종료 API 제거와 학부모 종료 API OpenAPI 노출
  - Flyway V5 적용과 Hibernate 매핑 검증

## 완료 체크리스트

- [x] 요구사항과 비요구사항을 구분했다.
- [x] 교사 답변 완료와 최종 종료 상태를 분리했다.
- [x] 학부모 권한과 잘못된 상태를 검증했다.
- [x] 상태 변경 경합을 민원 행 잠금과 재검증으로 보호했다.
- [x] 기존 운영 데이터 백필과 감사 이벤트를 추가했다.
- [x] API·ERD·패키지 문서를 갱신했다.
- [x] 로그와 응답에 민감 정보가 포함되지 않음을 확인했다.
- [x] 전체 테스트를 한 번 실행해 통과했다.
- [x] 배포 확인 지표와 롤백 조건을 정리했다.
- [x] AI 동작 변경이 없어 골든셋 평가가 불필요함을 확인했다.

## 프론트엔드 반영 사항

- 민원 상태 enum에 `ANSWERED`를 추가합니다.
- 교사 답변 전송 성공 후 반환되는 `complaintStatus=ANSWERED`를 처리합니다.
- 교사 미처리 화면은 `status=OPEN` 기준으로 조회합니다.
- 교사 화면의 별도 완료 버튼과 `/teacher/complaints/{id}/close` 호출을 제거합니다.
- 학부모 `ANSWERED` 화면에 `문의 완료`와 `추가 문의` 동작을 제공합니다.
- `문의 완료`는 `POST /complaints/{id}/close`를 호출합니다.
- `COMPLAINT_NOT_CLOSABLE` 발생 시 최신 상태를 다시 조회한 뒤 안내합니다.
- `ANSWERED`는 “답변 완료”, `CLOSED`는 “문의 종료” 등 서로 다른 라벨로 표시합니다.
- 자동 종료된 `CLOSED` 민원에서도 같은 사안의 추가 문의를 시작할 수 있습니다.

## 배포 확인

- 배포 전 프론트엔드가 `ANSWERED` enum을 처리할 수 있는지 확인합니다.
- Flyway V5 성공 후 `OPEN + waiting_for=PARENT` 건이 `ANSWERED`로 백필되었는지 확인합니다.
- 배포 후 다음 항목을 개인정보 없이 집계합니다.
  - `OPEN → ANSWERED` 전환 수
  - 학부모 `ANSWERED → CLOSED` 전환 수
  - `SYSTEM` 자동 종료 수
  - `COMPLAINT_NOT_CLOSABLE` 응답 비율
  - 교사 미처리 목록에 `ANSWERED`가 포함되지 않는지 여부
- 첫 자동 종료 실행 전 예상 대상 건수를 조회하여 급격한 일괄 종료를 방지합니다.

## 롤백 조건과 방법

다음 중 하나가 발생하면 자동 종료부터 중지합니다.

- 교사 답변 후 3일이 지나지 않은 민원이 종료됨
- 예상 대기 건수보다 많은 민원이 자동 종료됨
- 학부모 추가 문의 또는 문의 완료 요청과 상태 충돌이 반복됨
- 프론트엔드가 `ANSWERED`를 처리하지 못해 목록·상세 화면이 깨짐

1차 조치:

```text
COMPLAINT_AUTO_CLOSE_ENABLED=false
```

이 설정은 자동 종료만 중단하며 교사 답변 제출이 새 `ANSWERED` 데이터를 생성하는 것은 막지 않습니다.

이전 애플리케이션으로 롤백할 때는 다음 순서를 지킵니다.

1. `POST /teacher/complaints/{complaintId}/responses` 신규 요청을 인그레스 또는 점검 모드에서 차단합니다. 경로 단위 차단이 불가능하면 민원 쓰기 요청 전체를 중지합니다.
2. 이미 수락된 교사 답변 요청과 관련 DB 트랜잭션이 모두 끝날 때까지 drain합니다.
3. 자동 종료 작업이 중지된 상태에서 `ANSWERED` 데이터를 `OPEN + waiting_for=PARENT`로 변환합니다.
4. 변환 후 `ANSWERED` 잔여 건수가 0인지 확인합니다.
5. 이전 애플리케이션을 배포하고 상태 enum 조회와 교사 미처리 목록을 확인합니다.
6. 이전 애플리케이션이 정상임을 확인한 뒤 차단했던 요청을 재개합니다.

쓰기 차단을 유지하지 않은 채 데이터 변환과 구버전 배포를 진행하지 않습니다. V5에서 추가한 컬럼, 인덱스와 감사 이벤트는 삭제하지 않아 롤포워드 가능성을 유지합니다.

## 회고

```text
의도: 교사의 답변 완료와 민원의 최종 종료를 분리해 교사 부담과 학부모 추가 문의 기회를 함께 관리
검증: 상태 전이·권한·동시성·3일 경계·재개·Flyway 테스트와 전체 218개 테스트 통과
놓친 점: 최초 자동 종료 경계 테스트에서 cutoff 의미를 반대로 표현했으나 즉시 수정
다음 규칙: 기간 기반 정책은 기준 시각과 경계 포함 여부를 테스트 이름과 사례에 함께 명시
```
