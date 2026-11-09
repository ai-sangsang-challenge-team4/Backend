# TeacherHub 핵심 기능 명세

> 로컬 개발 참고 문서. Git 추적 대상이 아니다.
>
> 기준 코드: 현재 저장소와 PR #48 head `4584db7`
> 최종 갱신: 2026-11-09

## 1. 문서의 역할

이 문서는 기능을 수정할 때마다 전체 기획서를 다시 탐색하지 않도록 핵심 제품 계약을 모아 둔다.
API의 세부 요청·응답 형식은 `docs/` 문서를 참고하고, 이 문서는 다음 질문의 기준으로 사용한다.

- 누가 이 기능을 사용할 수 있는가?
- 어떤 상태에서 실행할 수 있는가?
- 성공하면 어떤 상태와 데이터가 바뀌는가?
- 위험하거나 실패한 경우 무엇을 차단하고 무엇을 허용하는가?
- 어떤 테스트를 반드시 유지해야 하는가?

기획서, 이 문서, 코드가 충돌하면 임의로 한쪽을 선택하지 않는다. 정책을 확인한 뒤 세 항목을
같은 작업에서 일치시킨다.

## 2. 사용자 역할

| 역할 | 핵심 권한 | 금지 사항 |
|---|---|---|
| 학부모 | 본인 자녀의 담당 교사에게 민원 작성, AI 검토, 추가 문의, 상태·대화 조회 | 다른 학부모 민원 접근, 제출 후 최초 내용 수정 |
| 교사 | 담당 민원 조회, 위험도 검토, 답변 작성·전송, 상담 완료, 관리자 공유 | 담당하지 않는 민원 접근, Emergency 잠금 우회 |
| 관리자 | 자신에게 배정된 공유·재검토 요청 조회 및 처리 | 배정되지 않은 요청 처리 |

모든 조회와 변경은 인증 사용자와 대상 리소스의 관계를 서버에서 다시 검증한다. 클라이언트가
전달한 사용자 ID나 역할만 신뢰하지 않는다.

## 3. 핵심 상태 모델

### 민원 상태

```text
DRAFT → OPEN → CLOSED
          ↑       │
          └───────┘ 같은 사안의 추가 문의 시 재오픈
```

- `DRAFT`: 최초 민원 작성 및 AI 검토 단계다.
- `OPEN`: 학부모와 교사가 대화하는 단계다.
- `CLOSED`: 교사가 상담 완료를 명시적으로 수행한 상태다.
- 교사 답변 전송만으로 자동 종료하지 않는다.

### 메시지 상태

```text
DRAFT → SENT → READ
```

- 임시저장 메시지는 상대방의 대화 내역에 노출하지 않는다.
- `waitingFor=PARENT|TEACHER|NONE`이 현재 작성 차례를 결정한다.
- 차례가 아닌 사용자는 새 메시지를 전송할 수 없다.

### 위험 상태

| 점수 | 등급 |
|---:|---|
| 0~20 | LOW |
| 21~40 | MEDIUM |
| 41~60 | HIGH |
| 61~70 | EMERGENCY |

기본 태그 점수는 `PROFANITY 6`, `INSULT 10`, `THREAT 25`, `LEGAL 4`,
`EXPOSURE 12`, `REPEAT 6`, `UNFAIR_REQUEST 8`, `PRIVACY 15`다.
최종 상태가 `PRESENT`인 태그만 한 번씩 합산하고 결과는
`min(70, 태그 점수 합계)`로 제한한다.
이 경우 `THREAT + EXPOSURE + INSULT`는 47점 HIGH가 된다. 직접 신체 위해처럼 긴급한
조건은 태그 합계로 Emergency를 만들지 않고 `RISK-03A`의 별도 Override를 적용한다.

## 4. 인증과 사용자

### AUTH-01 회원가입·로그인

- 학부모와 교사는 역할별 필수 참조 정보가 유효해야 가입할 수 있다.
- 로그인 성공 시 이후 API 인증에 사용하는 토큰을 발급한다.
- 가입 중복, 참조 정보 없음, 입력 오류는 서로 구분 가능한 업무 오류로 반환한다.

주요 API:

- `POST /auth/signup`
- `POST /auth/login`
- `GET /auth/schools`
- `GET /auth/schools/{schoolId}/classes`
- `GET /auth/classes/{schoolClassId}/students`

### USER-01 내 정보와 정책 동의

- 사용자는 자신의 프로필과 역할별 연결 정보를 조회한다.
- 정책 동의는 현재 정책 버전과 함께 기록한다.

주요 API:

- `GET /users/me`
- `POST /users/me/policy-consents`
- `GET /users/me/policy-consents`

## 5. 학부모 민원

### PARENT-01 최초 민원 초안

사전 조건:

- 선택한 학생이 로그인 학부모의 자녀다.
- 선택한 교사가 해당 학생 최신 학급의 담당 교사다.

동작:

1. `GET /complaints/recipients?studentId=...`로 문의 가능한 교사를 조회한다.
2. `POST /complaints`로 `DRAFT` 민원을 만든다.
3. `PATCH /complaints/{complaintId}`로 DRAFT 내용만 수정한다.
4. 동일 내용 저장은 불필요하게 content version을 올리지 않는다.

### PARENT-02 최초 민원 AI 검토와 제출

1. DRAFT 원문을 개인정보 마스킹한다.
2. `POST /complaints/{complaintId}/risk-analysis`로 위험 분석과 수정 제안을 생성한다.
3. 사용자는 제안을 직접 선택·수정한다. AI 수정안을 자동 적용하지 않는다.
4. `POST /complaints/{complaintId}/send`로 최종 내용을 제출한다.
5. 성공하면 `DRAFT → OPEN`, `waitingFor=TEACHER`가 된다.

분석을 먼저 호출하지 않아도 제출 과정에서 최종 내용 분석을 시도한다. AI 장애 시 제출을 허용하는
현재 정책은 `RISK-05`를 따른다.

### PARENT-03 목록·상세·상태 조회

- 학부모는 본인이 작성한 민원만 조회한다.
- 목록은 keyword, status, riskLevel, sort, page 조건을 지원하고 페이지 크기는 10으로 고정한다.

주요 API:

- `GET /complaints`
- `GET /complaints/{complaintId}`
- `GET /complaints/{complaintId}/status`
- `GET /complaints/{complaintId}/messages`

### PARENT-04 추가 문의

사전 조건:

- `OPEN + waitingFor=PARENT`이거나 같은 사안으로 `CLOSED` 민원을 다시 여는 경우다.

동작:

1. `PUT /complaints/{complaintId}/message-draft`로 초안을 생성·수정한다.
2. 기존 초안 수정과 전송은 `expectedContentVersion` 일치를 요구한다.
3. 메시지별 AI 검토는 1회, 수정된 최종 내용의 제출 분석까지 성공 분석 최대 2회다.
4. 전송 성공 시 메시지는 `SENT`, 민원은 `OPEN`, `waitingFor=TEACHER`가 된다.
5. 닫힌 민원을 다시 열면 상태 변경 이력을 남긴다.

## 6. 위험 분석

### RISK-01 개인정보 마스킹

- 외부 AI에는 민원 원문을 전달하지 않고 마스킹된 내용만 전달한다.
- 현재 구현이 직접 지원하는 패턴은 주민등록번호, 이메일, 전화번호다.
- 이름, 자유 형식 주소, 계좌번호 등은 현재 완전 탐지 대상이 아니다.
- Rule은 원문을 분석할 수 있지만, Rule의 evidence와 reason을 외부 AI에 넘기기 전에 다시 마스킹한다.
- 마스킹으로 원문 좌표가 달라질 수 있어 Claude에 전달하는 Rule evidence의 start/end는 `null`로 만든다.

### RISK-02 Rule → Claude 순차 위험 분석

PR #48 기준 목표 흐름:

```text
원문 유효성·권한·상태·재사용 검사
→ 원문 개인정보 마스킹
→ Rule이 원문 1차 탐지
→ Rule evidence/reason 마스킹
→ Claude에 마스킹 본문 + 마스킹 Rule 결과 전달
→ Claude가 문맥 기반 2차 판단
→ 태그별 최종 상태 결정
→ 점수·등급 계산 및 출처별 결과 저장
→ 수정안 생성
```

중요 계약:

- Rule과 Claude는 병렬 실행하지 않는다.
- `LlmRiskAnalyzer.analyze(maskedContent, ruleResults, repeatContext)`가 현재 내용, Rule 후보,
  동일 민원의 반복성 문맥을 받는다.
- Python AI 요청은 `complaint`, `ruleResults`, `repeatContext`를 `/api/v1/ai/analyze`로 전송한다.
- Rule 원본과 Claude 결과는 detector 상세 조회를 위해 출처별로 보존한다.

반복성 문맥 계약:

- 현재 분석 메시지와 동일한 민원의 최근 30일 전송 이력만 사용한다.
- 임시저장 메시지와 현재 분석 메시지는 이력에서 제외한다.
- 모든 과거 메시지는 외부 AI 전달 전에 개인정보를 마스킹한다.
- 횟수와 처리 상태는 30일 전체 이력으로 계산하되, 외부 AI에는 가장 최근 20개 메시지와
  마스킹 본문 합계 8,000자까지만 시간순으로 전달한다.
- 제한으로 일부 이력이 제외되거나 본문이 잘리면 `historyTruncated=true`로 전달한다.
- 기본 제한은 `risk.repeat.window-days`, `risk.repeat.max-messages`,
  `risk.repeat.max-characters` 설정으로 변경할 수 있다.
- 실제 사용자 ID와 이름은 전달하지 않고 `PARENT`, `TEACHER` 역할과 요청 안에서만 유효한
  `H1`, `H2` 형식의 참조값을 전달한다.

### RISK-03 최종 태그 결정 정책

태그별 우선순위는 다음과 같다.

1. Claude가 해당 태그 결과를 반환하면 Claude 상태가 최종 상태다.
2. Claude가 해당 태그를 반환하지 않은 경우에만 Rule 상태를 사용한다.
3. 둘 다 결과가 없으면 일반 태그는 `ABSENT`, `REPEAT`는 `UNKNOWN`이다.
4. `PRESENT`이지만 evidence가 없으면 `BOUNDARY`로 낮추고 점수에 포함하지 않는다.
5. Claude 결과가 있으면 최종 evidence와 reason도 Claude 결과를 사용한다.
6. Rule evidence/reason은 최종 결과와 별개로 출처 상세에 보존한다.

예시:

| Rule | Claude | 최종 |
|---|---|---|
| PRESENT | ABSENT | ABSENT |
| ABSENT | PRESENT | PRESENT |
| PRESENT | BOUNDARY | BOUNDARY |
| PRESENT | UNKNOWN | UNKNOWN |
| PRESENT | 결과 없음 | PRESENT |

이 Claude 우선 정책은 일반 위험 태그에 계속 적용한다. Rule이 `PRESENT`여도 Claude가 같은
태그를 `ABSENT`, `BOUNDARY`, `UNKNOWN`으로 반환하면 Claude 상태가 최종 상태다. 다만
Emergency 강제 판정은 일반 태그 병합 결과가 아니라 `RISK-03A`의 별도 hard guardrail
결정이므로 Claude가 해제할 수 없다.

### RISK-03A Emergency 강제 판정

#### 적용 대상

다음 중 하나가 명확한 근거와 함께 확인되면 태그 합계와 관계없이 Emergency Override를 적용한다.

1. **직접 신체 위해**: 식별 가능한 사람을 대상으로 살해·상해 등 신체 위해 행동과 실행 의도,
   명령 또는 계획이 함께 표현된 경우다.
2. **구체적 보복 계획**: 보복 대상과 행동이 명확하고 시점·장소·수단·접근 가능성 중 하나 이상이
   구체적으로 표현된 경우다.
3. **긴급 안전 위험**: 학생·교사의 안전에 현재 진행 중이거나 임박한 위험이 있고 대응 지연이
   피해 가능성을 높이는 경우다.

다음 조건은 Emergency 강제 판정에서 제외한다.

- 뉴스·사례·제3자 발언을 인용하거나 전달한 표현
- 위해 의도를 명시적으로 부정한 표현
- `죽여주는`처럼 위해 의미가 아닌 관용·속어 표현
- 대상·행동·의도가 불명확한 감정 표현이나 모호한 보복 암시
- 원문에서 Emergency 판단 근거 구간을 특정할 수 없는 결과

제외 여부가 불명확하면 Emergency를 자동 확정하지 않는다. 일반 `THREAT` 태그의
`BOUNDARY` 또는 `UNKNOWN`과 사람 검토 경로를 사용한다.

#### 판정과 점수

- 일반 태그는 `RISK-03`의 Claude 우선 정책으로 확정하고 기본 점수를 계산한다.
- 승인된 hard guardrail이 Emergency를 판정하면 `guardrailDecision=FORCE_EMERGENCY`,
  `effectiveRiskScore=max(baseRiskScore, 61)`, `effectiveRiskLevel=EMERGENCY`,
  `safetyLock=true`를 적용한다.
- `baseRiskScore`와 `baseRiskLevel`은 덮어쓰지 않고 강제 판정 전 결과로 보존한다.
- Claude가 동일 태그를 `ABSENT`, `BOUNDARY`, `UNKNOWN`으로 반환하거나 태그를 누락해도
  `FORCE_EMERGENCY`를 해제하지 않는다.
- 재분석 결과가 낮아져도 safety lock은 자동 해제하지 않는다. 지정 검토자의 판단과 명시적인
  잠금 해제가 필요하다.

#### 현재 룰 데이터의 사용 가능 범위

현재 룰 사전에는 바로 Emergency를 확정할 수 있는 승인 완료 룰이 없다.

| rule_id | 표현 | 현재 역할 | 판정 |
|---|---|---|---|
| RD-THREAT-09 | `죽여` | `SIGNAL`, `EMERGENCY_CANDIDATE`, `context_required=TRUE` | `죽여주는` 오탐이 있어 단독 확정 금지 |
| RD-THREAT-10 | `죽이겠` | `SIGNAL`, `EMERGENCY_CANDIDATE`, `context_required=TRUE` | 대상·인용·부정 여부 확인 전 단독 확정 금지 |

`THREAT_INTIMIDATION`의 나머지 룰도 모두 문맥 확인이 필요한 신호다. 따라서 기존 키워드를
그대로 hard guardrail로 승격하지 않는다. Emergency 강제 룰은 안정적인 rule ID, 승인 상태,
대상·행동·의도 조건, 예외 조건, evidence 요구사항과 룰셋 버전을 별도로 가져야 한다.

#### 처리와 감사

- Emergency에서는 AI 답변 초안, 임시저장, 전송과 상담 완료를 차단한다.
- 증빙 보존과 공식 오프라인 대응 절차를 안내하되 AI가 관리자 공유, 외부 신고 또는 메시지
  전송을 자동 실행하지 않는다.
- 발동한 hard rule ID와 버전, 근거, 기본/적용 점수와 등급, safety lock 변경, 사람 검토 결과를
  원문 개인정보 없이 감사 가능하게 기록한다.
- hard guardrail 판정 자체가 실패하면 Emergency로 단정하지 않고 `REVIEW_REQUIRED`와
  안전 잠금을 적용하는 방향을 기본안으로 한다. 제출 허용 범위와 운영 알림은 별도 실패 정책으로
  확정한 뒤 구현한다.

### RISK-04 분석 재사용과 수정안

- 같은 메시지와 같은 내용 hash의 완료 분석이 있으면 재사용하고 AI를 다시 호출하지 않는다.
- 최초 사용자 AI 검토 이후 내용이 달라졌다면 제출 내부에서 최종 분석을 한 번 수행한다.
- 메시지별 성공 완료 분석은 최대 2회다.
- 제출 최종 분석은 새 수정안을 생성하지 않는다.
- 수정안은 최종 위험 태그만 사용하며 사용자에게 자동 적용하지 않는다.
- 모델명과 temperature, Rule/Claude 출처 상태와 근거를 저장한다.

### RISK-05 실패 정책

- 일반 AI 검토 요청에서 마스킹, Rule, Claude 또는 수정안 생성이 실패하면 요청을 실패시킨다.
- 제출 중 마스킹 또는 분석이 실패하면 현재 구현은 분석을 건너뛰고 원문 제출을 계속한다.
- 마스킹이 실패하면 원문을 Claude에 보내지 않는다.
- Rule 성공 후 Claude 호출 전체가 실패해도 Rule 결과만 저장하는 fallback은 현재 없다.
- Claude가 정상 응답했지만 특정 태그를 누락한 경우에는 해당 태그에 Rule 결과를 사용한다.

`fail-open` 정책은 안전·사용성 결정이므로 변경 시 제출 테스트, 사용자 안내와 운영 알림을 함께
수정해야 한다.

### RISK-06 분석 조회

- `GET /risk-analyses/{analysisId}/findings`: 최종 감지 태그, 근거, 태그별 수정 제안과 전체 수정본
- `GET /risk-analyses/{analysisId}/detector-results`: Rule/Claude 출처별 상태·근거와 모델 정보
- 미완료 분석과 다른 사용자의 분석은 조회할 수 없다.

## 7. 교사 처리

### TEACHER-01 목록·상세·대화 조회

- 교사는 담당 민원만 조회한다.
- 제출되지 않은 DRAFT 민원과 DRAFT 메시지는 목록·대화에서 제외한다.
- 학부모 메시지의 최신 완료 위험도가 LOW이면 원문, 그보다 높으면 완충 요약을 기본 제공한다.
- 원문 열람은 별도 API로 수행하고 감사 이벤트를 남긴다.
- 분석이 없으면 대화는 반환하되 수동 검토 요청 행동을 제공한다.

### TEACHER-02 위험도 검토

- 교사는 1~7 척도로 검토하고 서버는 10~70 점수로 환산한다.
- 현재 점수보다 낮추면 사유가 필수다.
- EMERGENCY 하향은 관리자 재검토 요청을 생성하고 안전 잠금을 유지한다.
- 시스템 분석을 덮어쓰지 않고 검토 이력을 누적한다.

### TEACHER-03 AI 초안·임시저장·전송

- AI 초안은 참고용이며 교사가 검토·수정한 최종 내용만 전송한다.
- EMERGENCY 또는 safety lock 상태에서는 AI 초안, 저장, 전송을 모두 차단한다.
- HIGH는 safety lock이 없다면 AI 초안을 생성할 수 있다.
- 최종 제출에는 최대 100자의 `Idempotency-Key`가 필수다.
- 같은 교사·같은 키·같은 내용의 재요청은 메시지와 이벤트를 중복 생성하지 않는다.
- 답변 전송 후 민원은 `OPEN`을 유지하고 `waitingFor=PARENT`가 된다.

### TEACHER-04 상담 완료

- 교사가 별도 완료 API를 호출해야 `CLOSED`, `waitingFor=NONE`이 된다.
- 답변이 필요한 상태, EMERGENCY 또는 safety lock 상태에서는 완료할 수 없다.

## 8. 관리자 공유와 Emergency 재검토

### ADMIN-01 관리자 공유

- 교사는 EMERGENCY 민원을 지정 관리자에게 공유한다.
- 공유 범위와 사유를 명시하며, 관리자는 자신에게 배정된 공유만 조회·처리한다.
- 관리자는 `SHARED → UNDER_REVIEW → COMPLETED` 흐름으로 검토한다.

### ADMIN-02 Emergency 하향 재검토

- EMERGENCY를 낮추려는 교사 요청은 즉시 적용하지 않는다.
- 지정 검토자가 승인해야 요청 점수가 반영된다.
- 승인만으로 safety lock을 자동 해제하지 않는다.
- 지정 검토자가 명시적으로 잠금을 해제하기 전까지 EMERGENCY 행동 제한을 유지한다.
- 처리 중이거나 잠금이 유지된 중복 요청을 차단한다.

## 9. 감사와 운영 불변식

- 상태 변경, 위험 분석, 위험 검토, 원문 열람, 메시지 읽음·전송, AI 초안 생성,
  관리자 공유와 안전 차단은 감사 이벤트로 남긴다.
- 감사 이벤트와 실제 상태 변경은 가능한 한 같은 트랜잭션에서 처리한다.
- 로그에는 민원 원문, 마스킹 전 개인정보, 토큰과 인증 정보를 남기지 않는다.
- AI 입력·결과 변경을 추적할 수 있도록 모델과 프롬프트 버전, 데이터셋 버전을 관리한다.

## 10. 기능 변경 체크리스트

기능을 만들거나 수정할 때 아래 항목을 먼저 복사해 작업 계획에 붙인다.

```text
[ ] 대상 역할과 소유권 검증이 정의됐는가?
[ ] 실행 가능한 민원·메시지·위험 상태가 정의됐는가?
[ ] 성공 후 상태 전이와 waitingFor가 정의됐는가?
[ ] 재시도, 중복 요청, 동시 요청을 처리하는가?
[ ] 개인정보가 외부 AI·로그·응답에 노출되지 않는가?
[ ] Rule과 Claude 중 누가 최종 결정하는지 태그별로 명확한가?
[ ] AI/마스킹 장애 시 fail-open 또는 fail-closed가 명확한가?
[ ] 감사 이벤트가 필요한가?
[ ] 정상·권한·잘못된 상태·경계값·외부 장애 테스트가 있는가?
[ ] API 명세와 이 핵심 기능 명세를 함께 갱신했는가?
```

## 11. PR #48 반영 확인 목록

PR이 현재 작업 브랜치에 반영되면 다음을 확인한다.

- [ ] `CompletableFuture` 기반 Rule/LLM 병렬 코드가 제거됐다.
- [ ] Rule 탐지가 Claude 호출보다 먼저 실행된다는 순서 테스트가 통과한다.
- [ ] Rule 결과의 evidence/reason이 마스킹된 뒤 Claude에 전달된다.
- [ ] Python 요청 JSON에 `complaint`, `ruleResults`가 포함된다.
- [ ] Claude가 반환한 태그는 Claude 상태가 최종값이 된다.
- [ ] Claude가 누락한 태그만 Rule로 fallback한다.
- [ ] Rule/Claude 출처별 결과는 상세 조회용으로 모두 보존된다.
- [ ] `PRESENT`이지만 evidence가 없는 결과는 `BOUNDARY`가 된다.
- [ ] 현재 브랜치의 `docs/risk-api.md`에 남은 "병렬 실행" 설명을 순차 흐름으로 수정한다.
- [ ] 골든셋에서 Rule 하향 override의 안전성을 별도로 평가한다.
