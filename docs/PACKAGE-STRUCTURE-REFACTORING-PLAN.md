# TeacherHub 패키지·파일 이름 리팩터링 계획

## 1. 작업 목적

TeacherHub의 기능 단위 패키지 구조는 유지하면서 다음 문제를 정리한다.

- 학부모용·교사용 API 타입을 파일 이름만으로 구분하기 어렵다.
- 공용 조회와 역할 전용 조회가 같은 서비스에 섞여 있다.
- 한 컨트롤러가 조회, 메시지, 위험도 검토, 답변 전송을 모두 담당한다.
- 일부 테스트 패키지가 실제 코드 패키지와 다르다.
- Safety Flag 같은 공통 위험 응답을 역할별 DTO 안에서 중복 구현할 가능성이 있다.
- 의미가 불명확한 일반 이름과 불필요하게 한 단계 더 깊은 폴더가 있다.

업무 규칙 한 문장:

> 역할별 API 경계는 Parent·Teacher·Admin 이름으로 명확히 구분하고, 실제로 공유되는 도메인과 위험 분석 응답만 공통 패키지에 둔다.

## 2. 브랜치 기준

현재 작업 브랜치는 다음과 같다.

```text
refactor/#70-package-naming
```

이 브랜치는 `dev`의 `201aa67`에서 시작했으며, 이전 작업인
`feature/#68-teacher-safety-signal`의 `25cd601`을 포함하지 않는다.
따라서 #68을 병합하지 않고 패키지 정리 작업을 독립적으로 진행할 수 있다.

주의 사항:

- #68의 `EmergencyOverride`, `SafetySignalCode` 구현을 이 브랜치에 임의로 복사하지 않는다.
- #68이 먼저 `dev`에 병합되면 이 브랜치에 `dev`를 반영한 뒤 공통 DTO 위치를 정리한다.
- #70이 먼저 진행되면 #68 쪽에서 변경된 클래스 이름과 패키지를 후속으로 맞춘다.
- 두 브랜치의 변경을 한 PR에 섞지 않는다.

작업 시작 확인:

```powershell
git branch --show-current
git status --short
git log -3 --oneline --decorate
```

## 3. 현재 구조 평가

### 유지할 구조

최상위 기능 패키지는 현재 구조가 적절하다.

```text
auth
complaint
risk
escalation
user
school
security
system
ai
common
```

`AGENTS.md`의 기능 단위 패키지 원칙과 일치하므로 최상위 패키지를 계층형 공통 폴더로 다시 묶지 않는다.

다음 세부 패키지도 역할이 명확하므로 유지한다.

- `complaint.parent.dto.complaint`
- `complaint.parent.dto.message`
- `complaint.teacher.dto.query`
- `complaint.teacher.dto.reply`
- `risk.dto.llm`: 외부 AI 입력·출력 계약
- `risk.dto.response`: 사용자 API 응답 계약
- 기능별 `controller`, `service`, `repository`

### 우선 개선할 구조

1. `complaint.parent` 내부의 DTO 중 파일 이름에 `Parent`가 없는 타입
2. 학부모 전용 상세 조회와 공용 목록 조회가 섞인 `ComplaintQueryService`
3. 여러 유스케이스가 섞인 `ParentComplaintController`, `TeacherComplaintController`
4. 실제 코드는 `escalation`인데 테스트는 `complaint.escalation`인 패키지 불일치
5. 용도를 알 수 없는 `risk.config.DataInitializer`
6. 단일 서비스만 들어 있는 `complaint.history.service`

## 4. 이름 규칙

### 역할 접두사 적용 대상

다음 타입에는 역할 접두사를 붙인다.

- 외부 API 요청·응답 DTO
- 역할 전용 Controller
- 역할 전용 Service
- 역할별 화면 계약을 나타내는 enum 또는 action DTO

예시:

```text
ParentComplaintCreateRequest
ParentComplaintSubmissionResponse
TeacherComplaintDetailResponse
TeacherResponseSubmissionResponse
AdminEscalationSummaryResponse
```

### 역할 접두사를 붙이지 않는 대상

다음 타입은 여러 역할이 실제로 공유하므로 역할 접두사를 붙이지 않는다.

- Entity
- Repository
- 공통 상태 enum
- 공통 목록 조회 조건·응답
- 공통 위험 분석 응답
- 공통 예외

예시:

```text
Complaint
ComplaintRepository
ComplaintStatus
WaitingFor
ComplaintListQuery
ComplaintPageResponse
RiskLevel
risk/dto/response/common/EmergencyOverrideResponse
```

패키지 안에 있다는 이유만으로 모든 내부 클래스에 Parent 또는 Teacher를 반복해서 붙이지 않는다.
API 경계와 역할 전용 유스케이스를 우선 명확하게 한다.

## 5. 파일 이름 변경안

### 학부모 민원 DTO

| 현재 이름 | 변경 이름 | 비고 |
|---|---|---|
| `ComplaintCreateRequest` | `ParentComplaintCreateRequest` | 최초 민원 생성 요청 |
| `ComplaintDraftUpdateRequest` | `ParentComplaintDraftUpdateRequest` | 최초 민원 초안 수정 |
| `ComplaintRecipientResponse` | `ParentComplaintRecipientResponse` | 학부모가 선택할 교사 목록 |
| `ComplaintResponse` | `ParentComplaintStateResponse` | 생성·상태 조회의 상태 응답 |
| `ComplaintSubmissionResponse` | `ParentComplaintSubmissionResponse` | 최초 민원 전송 응답 |
| `ComplaintConversationResponse` | `ParentComplaintConversationResponse` | 학부모 대화 조회 |
| `ComplaintDetailResponse` | `ParentComplaintDetailResponse` | 학부모 전용 상세 조회, 패키지도 이동 |

`ComplaintDetailResponse` 이동:

```text
현재: complaint/query/dto/ComplaintDetailResponse.java
변경: complaint/parent/dto/complaint/ParentComplaintDetailResponse.java
```

### 교사 DTO

| 현재 이름 | 변경 이름 | 비고 |
|---|---|---|
| `AllowedAction` | `TeacherAllowedAction` | 교사 화면에서 가능한 행동 |

이미 `Teacher` 접두사가 있는 아래 DTO는 유지한다.

- `TeacherComplaintDetailResponse`
- `TeacherConversationResponse`
- `TeacherHistoryResponse`
- `TeacherOriginalViewResponse`
- `TeacherRiskResponse`
- `TeacherAiDraftResponse`
- `TeacherResponseDraftResponse`
- `TeacherResponseRequest`
- `TeacherResponseSubmissionResponse`
- `TeacherRiskReviewRequest`
- `TeacherRiskReviewResponse`

### 위험 분석 설정

| 현재 이름 | 변경 이름 | 이유 |
|---|---|---|
| `DataInitializer` | `RiskTagDataInitializer` | 위험 태그 기준값 초기화라는 의도를 표시 |

## 6. 공용 조회 서비스 분리

현재 `ComplaintQueryService`는 다음 두 책임을 함께 가진다.

- 학부모·교사가 공유하는 민원 목록 조회
- 학부모 전용 민원 상세 조회

다음처럼 분리한다.

```text
complaint/query/service/ComplaintListQueryService.java
  - findComplaints
  - 목록용 최신 분석·검토 조립

complaint/parent/service/ParentComplaintQueryService.java
  - findComplaint
  - 학부모 소유권 검증
  - ParentComplaintDetailResponse 조립

complaint/teacher/service/TeacherComplaintQueryService.java
  - 기존 교사 상세·대화·위험도·이력 조회 유지
```

공용 목록 DTO는 이름과 위치를 유지한다.

```text
ComplaintListQuery
ComplaintListResponse
ComplaintListSort
ComplaintPageResponse
```

공용 목록 응답이 향후 역할별 필드 차이로 복잡해지면 그때
`ParentComplaintListResponse`와 `TeacherComplaintListResponse`로 분리한다.
현재는 `counterpartName`, `counterpartRole`로 공유 가능하므로 미리 분리하지 않는다.

## 7. Controller 분리

API URL은 변경하지 않고 Java Controller 책임만 분리한다.

### 학부모

```text
ParentComplaintController
  - 민원 생성
  - 민원 초안 수정
  - 문의 대상 교사 조회
  - 최초 민원 전송
  - 상태·목록·상세 조회

ParentMessageController
  - 추가 문의 초안 저장·조회
  - 추가 문의 전송
  - 대화 조회
```

둘 다 필요하면 `@RequestMapping("/complaints")`를 사용할 수 있다.
Spring 매핑 충돌이 없도록 메서드별 경로를 테스트한다.

### 교사

```text
TeacherComplaintController
  - 목록·상세·대화·이력 조회
  - 원문 별도 열람

TeacherResponseController
  - AI 답변 초안 생성
  - 답변 초안 저장·조회
  - 답변 전송
  - 상담 완료

TeacherRiskReviewController
  - 위험 분석 조회
  - 교사 위험도 검토
```

컨트롤러를 나눠도 다음 API 경로와 JSON 계약은 변경하지 않는다.

- `/complaints/**`
- `/teacher/complaints/**`

## 8. 폴더 병합·이동안

### 병합 또는 평탄화

```text
현재: complaint/history/service/ComplaintHistoryService.java
변경: complaint/history/ComplaintHistoryService.java
```

`history` 아래에 Controller, Repository 등 추가 계층이 없고 서비스 한 개뿐이므로 한 단계를 줄인다.

빈 폴더가 있다면 삭제한다.

```text
src/test/java/com/teacherhub/complaint/service
```

### 테스트 패키지 정렬

실제 코드 위치에 맞게 테스트를 이동한다.

```text
현재: src/test/java/com/teacherhub/complaint/escalation/**
변경: src/test/java/com/teacherhub/escalation/**
```

테스트의 `package` 선언도 `com.teacherhub.escalation...`으로 변경한다.

### 유지할 폴더

파일 수가 한 개라는 이유만으로 아래 표준 계층을 무조건 합치지 않는다.

- `controller`
- `service`
- `repository`
- `entity`
- `exception`

기능 패키지 안에서 역할을 빠르게 찾는 탐색 기준이므로 일관성을 우선한다.

## 9. 공통 Emergency 응답 위치

교사와 학부모의 역할별 DTO 안에 긴급 승격 응답을 중복해서 두지 않는다.

권장 위치:

```text
risk/dto/response/common/EmergencyOverrideResponse.java
```

권장 구조:

```text
EmergencyOverrideResponse
├─ applied
└─ safetySignals
   ├─ code
   ├─ title
   └─ description
```

사용 대상:

- 학부모 `ParentRiskSummaryResponse`
- 교사 `TeacherRiskResponse`
- 필요하면 관리자 위험 조회 응답

원시 `SafetyFlags`는 `risk.dto.llm`에 유지한다. `SafetyFlags`는 외부 AI 계약이고,
`EmergencyOverrideResponse`는 사용자 화면 계약이므로 같은 타입으로 합치지 않는다.

## 10. 목표 패키지 구조

```text
complaint/
├─ access/
├─ entity/
├─ enums/
├─ exception/
├─ repository/
├─ history/
│  └─ ComplaintHistoryService.java
├─ query/
│  ├─ dto/
│  │  ├─ ComplaintListQuery.java
│  │  ├─ ComplaintListResponse.java
│  │  ├─ ComplaintListSort.java
│  │  └─ ComplaintPageResponse.java
│  ├─ repository/
│  │  └─ ComplaintSearchQueryRepository.java
│  └─ service/
│     └─ ComplaintListQueryService.java
├─ parent/
│  ├─ controller/
│  │  ├─ ParentComplaintController.java
│  │  └─ ParentMessageController.java
│  ├─ service/
│  │  ├─ ParentComplaintService.java
│  │  ├─ ParentComplaintQueryService.java
│  │  └─ ParentMessageService.java
│  └─ dto/
│     ├─ complaint/
│     │  ├─ ParentComplaintCreateRequest.java
│     │  ├─ ParentComplaintDraftUpdateRequest.java
│     │  ├─ ParentComplaintRecipientResponse.java
│     │  ├─ ParentComplaintStateResponse.java
│     │  ├─ ParentComplaintSubmissionResponse.java
│     │  └─ ParentComplaintDetailResponse.java
│     └─ message/
│        ├─ ParentComplaintConversationResponse.java
│        ├─ ParentMessageRequest.java
│        ├─ ParentMessageResponse.java
│        └─ ParentMessageSendRequest.java
├─ teacher/
│  ├─ controller/
│  │  ├─ TeacherComplaintController.java
│  │  ├─ TeacherResponseController.java
│  │  └─ TeacherRiskReviewController.java
│  ├─ dto/
│  │  ├─ query/
│  │  └─ reply/
│  ├─ policy/
│  ├─ provider/
│  └─ service/
└─ template/

risk/
├─ analyzer/                         # Rule·LLM 분석 경계
├─ controller/                       # 위험 분석·재검토 HTTP API
├─ dto/
│  ├─ detection/                     # 서버 내부 탐지 결과
│  ├─ llm/                           # 외부 AI 입출력 계약
│  ├─ response/
│  │  ├─ common/                     # 역할 공통 안전 신호 응답
│  │  │  └─ EmergencyOverrideResponse.java
│  │  ├─ parent/                     # 학부모 위험 분석 단계별 응답
│  │  │  ├─ ParentRiskSummaryResponse.java
│  │  │  ├─ ParentRiskFindingsResponse.java
│  │  │  └─ ParentRiskDetectorResponse.java
│  │  └─ teacher/                    # 교사 위험 조회 응답
│  │     └─ TeacherRiskResponse.java
│  └─ review/                        # 교사·관리자 위험 재검토 요청/응답
├─ entity/
├─ enums/
├─ exception/
├─ masking/
├─ policy/
│  └─ TeacherRiskScale.java
├─ repository/
└─ service/
   ├─ ParentRiskAnalysisQueryService.java
   ├─ TeacherRiskQueryService.java
   └─ TeacherRiskReviewService.java
```

`complaint.teacher.controller`는 교사 URL·인증·요청 변환을 담당하고, 위험 조회·검토 정책과
응답 조립은 `risk`가 담당한다. 따라서 `risk`가 `complaint.teacher`의 DTO나 정책을 역참조하지 않는다.
학부모 `ParentRiskFindingsResponse`와 교사 `TeacherRiskResponse`의 태그별 중첩 타입은
`RiskTagResult`로 통일하되, 역할별 데이터 최소화를 위해 필드 구성은 각각 유지한다.
Java 내부에서는 태그 목록을 `riskTagResults`, 태그 근거를 `evidence`로 통일한다.
프론트 계약은 `@JsonProperty`로 학부모의 `findings`·`evidences`, 교사의
`detectedFactors`·`evidenceSpans`를 그대로 유지한다.
`escalation`, `auth`, `user` 등 다른 최상위 기능 패키지는 이번 작업에서 대규모 이동하지 않는다.

## 11. 작업 순서

### 1단계: 역할별 DTO 이름 정리

- Parent DTO 이름 변경
- `ComplaintDetailResponse` 이동 및 이름 변경
- `AllowedAction` 이름 변경
- import와 테스트 이름 동기화
- Swagger 스키마 이름 변화 확인

개발 중 검증:

```powershell
.\gradlew.bat test --tests "com.teacherhub.complaint.parent.*"
.\gradlew.bat test --tests "com.teacherhub.complaint.teacher.*"
```

### 2단계: 공용 조회 서비스 분리

- `ComplaintQueryService`를 `ComplaintListQueryService`로 축소
- 학부모 상세 조회를 `ParentComplaintQueryService`로 이동
- Controller 의존성 수정
- 학부모·교사 목록 필터 테스트 유지

### 3단계: Controller 분리

- `ParentMessageController` 분리
- `TeacherResponseController` 분리
- `TeacherRiskReviewController` 분리
- URL과 HTTP 상태 코드가 이전과 같은지 Controller 테스트로 검증

### 4단계: 폴더 평탄화와 테스트 패키지 정렬

- `ComplaintHistoryService` 이동
- escalation 테스트 패키지 이동
- 빈 폴더 제거
- 테스트 클래스 이름과 실제 대상 일치 확인

### 5단계: 공통 위험 응답 정리

- `EmergencyOverrideResponse`를 학부모 `ParentRiskSummaryResponse`와 교사 `TeacherRiskResponse`에서 공통 사용
- 원시 `SafetyFlags`와 사용자 화면 계약 분리
- 교사 위험 조회·검토 DTO와 서비스를 `risk`로 이동

### 6단계: 최종 검증

이번 변경은 여러 Controller·DTO·공용 조회 경계에 영향을 주므로 전체 테스트를 한 번 실행한다.

```powershell
.\gradlew.bat test
```

`test`와 `clean test`를 연속 실행하지 않는다. Gradle 설정이나 리소스 처리를 바꾸지 않으므로 기본적으로 `test`면 충분하다.

## 12. 비요구사항

이번 리팩터링에서 다음은 변경하지 않는다.

- API URL
- HTTP 메서드와 상태 코드
- JSON 필드 이름
- 민원 상태 전이
- `waitingFor` 정책
- 개인정보 마스킹 정책
- AI 프롬프트·모델·위험 점수 계산
- DB Entity와 테이블 이름
- Flyway migration
- 위험 분석 재사용·횟수 제한 정책

`RiskAnalysisService` 책임 분리는 필요하지만 1,000줄 이상의 안전 핵심 로직이므로 별도 이슈와 PR에서 진행한다.

## 13. API 호환성 주의 사항

Java 클래스 이름을 바꾸면 JSON 필드는 유지되더라도 OpenAPI 스키마 이름이 달라질 수 있다.

확인 사항:

- 프론트가 OpenAPI 타입을 자동 생성하는지 확인
- Swagger component schema 이름 변경을 프론트에 전달
- `@Schema(name = "...")`를 사용하는 중첩 DTO 이름 확인
- API 경로와 JSON 스냅샷이 동일한지 Controller 테스트로 검증

호환성이 필요한 경우 일시적으로 `@Schema(name = "기존이름")`를 유지할 수 있지만,
새 이름으로 통일할 계획이라면 프론트 변경과 같은 배포 단위로 맞춘다.

## 14. 커밋 분리 권장안

한 커밋에 모든 이동을 넣지 않는다.

```text
rename: 학부모 민원 DTO에 역할 접두사 적용
refactor: 민원 목록 조회와 학부모 상세 조회 서비스 분리
refactor: 학부모와 교사 민원 컨트롤러를 유스케이스별로 분리
rename: escalation 테스트 패키지를 실제 기능 경로로 이동
refactor: Emergency 승격 응답을 공통 위험 DTO로 분리
```

파일 이동은 `git status --short`와 `git diff --cached --name-status`로 rename 인식 여부를 확인한다.
`git add .` 또는 `git add -A`를 사용하지 않고 변경 경로만 명시적으로 스테이징한다.

## 15. 완료 기준

- [ ] 역할 전용 API 타입은 Parent·Teacher·Admin 접두사로 구분된다.
- [ ] 공용 타입에는 불필요한 역할 접두사가 없다.
- [ ] 학부모 전용 상세 DTO가 공용 query 패키지에 남아 있지 않다.
- [ ] 공용 목록 조회와 역할 전용 상세 조회 서비스가 분리됐다.
- [ ] 컨트롤러가 조회·메시지·답변·위험 검토 유스케이스로 분리됐다.
- [ ] API URL, HTTP 상태, JSON 필드가 변경되지 않았다.
- [ ] escalation 테스트 패키지가 실제 코드 위치와 일치한다.
- [ ] 불필요한 빈 폴더가 없다.
- [ ] Swagger 스키마 이름 변경을 확인했다.
- [ ] 전체 `test`가 한 번 통과했다.
- [ ] 관련 없는 파일을 변경하지 않았다.

## 16. 배포 확인과 롤백 조건

배포 후 확인:

- 학부모·교사 민원 목록·상세·대화 API 성공률
- 추가 문의 초안 저장과 전송 API의 404·409 비율
- 교사 답변 초안·전송 API 성공률
- Swagger/OpenAPI 생성 성공 여부
- 프론트 타입 생성과 빌드 성공 여부

롤백 조건:

- 기존 API 경로가 404 또는 405를 반환함
- JSON 필드 누락으로 프론트 렌더링이 실패함
- 역할별 권한 검증 경로가 달라짐
- 메시지 상태 또는 `waitingFor` 전이가 달라짐

DB 변경이 없으므로 문제 발생 시 애플리케이션 커밋만 이전 버전으로 되돌린다.

## 17. 다음 세션 시작 체크리스트

```text
1. AGENTS.md와 이 문서를 읽는다.
2. 현재 브랜치가 refactor/#70-package-naming인지 확인한다.
3. git status --short로 사용자 변경이 없는지 확인한다.
4. feature/#68-teacher-safety-signal의 병합 여부를 확인한다.
5. 1단계 Parent DTO rename부터 시작한다.
6. API 경로와 JSON 필드는 변경하지 않는다.
7. 파일 이동 후 타깃 테스트를 실행한다.
8. 모든 단계 완료 후 전체 test를 한 번 실행한다.
```

## 18. 작업 회고 작성 틀

```text
의도: 역할별 API 경계와 공용 기능의 위치를 파일 이름과 패키지만으로 파악할 수 있게 한다.
검증: 역할별 Controller 테스트, 공용 목록 조회 테스트, 전체 test 결과를 기록한다.
놓친 점: 작업 중 발견한 잘못된 역할 의존성이나 OpenAPI 호환성 문제를 기록한다.
다음 규칙: 역할 전용 DTO는 생성 시점부터 접두사를 붙이고 공용 DTO는 실제 두 역할 이상이 사용할 때만 만든다.
```

