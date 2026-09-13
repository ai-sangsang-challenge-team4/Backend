# H2 개발 데이터로 Postman 테스트하기

## 1. dev 프로필로 앱 실행

IntelliJ에서 `Run > Edit Configurations`를 열고 `TeacherhubApplication` 실행 설정의
`Active profiles`에 `dev`를 입력한 뒤 실행합니다.
해당 항목이 보이지 않으면 `Program arguments`에 `--spring.profiles.active=dev`를 입력합니다.

터미널에서는 다음 명령으로 실행할 수 있습니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

`Dev data ready: parentEmail=parent@test.com, studentId=1, ...` 로그까지 확인합니다.
기존에 8080 포트로 앱을 실행 중이라면 먼저 종료하고 dev 프로필로 다시 실행합니다.

dev 전용 H2 메모리 DB에 다음 데이터가 생성됩니다. 별도 DB 설치나 회원가입은 필요 없습니다.

| 데이터 | 값 |
| --- | --- |
| 학부모 이메일 | `parent@test.com` |
| 교사 이메일 | `teacher@test.com` |
| 두 계정의 비밀번호 | `Test1234!` |
| 학생 | `Demo Student`, 학번 `DEV-20260001` |
| 학급 | 2026학년도 3학년 2반 |
| 연결 관계 | 학부모–학생, 학생–학급, 학급–담임교사 |

비밀번호는 PasswordEncoder로 인코딩하여 저장합니다.
`dev` 프로필을 지정하지 않으면 초기화 코드는 실행되지 않습니다.
dev DB는 실행할 때 새로 생성되므로 재시작하면 작성한 민원도 사라집니다.
`application-dev.properties`는 메모리 DB와 `create-drop`을 전제로 하므로 영구 DB용으로 사용하지 않습니다.

## 2. Postman 요청 모음 가져오기

Postman의 `Import`로 같은 폴더의 `teacherhub-dev.postman_collection.json`을 가져옵니다.
컬렉션 변수의 `baseUrl`은 `http://localhost:8080`, `studentId`는 `1`입니다.
실행 로그에 다른 학생 ID가 보이면 `studentId`를 로그 값으로 수정합니다.

아래 요청을 번호 순서대로 Send 합니다. 로그인 토큰과 생성된 민원 ID는
응답 후 스크립트가 컬렉션 변수에 자동 저장합니다. 나머지 요청은 컬렉션의 Bearer 인증을 상속합니다.

## 3. 학부모 로그인

`POST {{baseUrl}}/auth/login` (인증 없음)

Body는 raw / JSON을 사용합니다.

```json
{"email":"parent@test.com","password":"Test1234!"}
```

예상: `200 OK`. 응답의 `accessToken`이 자동 저장됩니다.
수동 요청을 만들 경우 이후 요청의 Authorization에서 Bearer Token을 선택하고 토큰을 붙여 넣습니다.

## 4. 초안 작성

`POST {{baseUrl}}/complaints`

```json
{"studentId":1,"content":"아이의 학교생활에 대해 문의드립니다."}
```

예상: `201 Created`, `{"complaintId":1,"status":"DRAFT"}`.
민원 ID는 생성할 때마다 달라지며 컬렉션이 실제 응답 값을 저장합니다.

## 5. 검토 요청

`POST {{baseUrl}}/complaints/{{complaintId}}/review` (Body 없음)

예상: `200 OK`. `originalContent`, `riskyExpressionCount`, `riskyExpressions`,
`partialRevision`, `aiRevision`을 반환합니다.
현재 AI는 Mock이므로 입력 내용과 관계없이 고정된 검토 결과가 나옵니다.
개인정보 마스킹도 아직 구현되지 않았습니다.

## 6. 내용 수정

`PATCH {{baseUrl}}/complaints/{{complaintId}}`

```json
{"content":"아이의 학교생활에 대해 확인 후 상담을 요청드립니다."}
```

예상: `204 No Content`. 응답 본문이 없는 것이 정상입니다.
제출 전에 검토 요청을 다시 보내면 `originalContent`에서 수정 내용을 확인할 수 있습니다.

## 7. 최종 제출

`POST {{baseUrl}}/complaints/{{complaintId}}/send` (Body 없음)

헤더: `Idempotency-Key: {{$guid}}`

예상: `200 OK`, 응답 본문 없음. 서버에서 담임교사를 지정하고 상태를 `RECEIVED`로 변경합니다.
컬렉션은 요청마다 UUID를 생성합니다. 이미 제출된 동일 민원은 새 키로도 다시 제출할 수 없습니다.
다시 정상 흐름을 테스트하려면 초안 작성 단계부터 새 민원을 만듭니다.
현재 민원 상세 조회 API가 없어 제출 응답에는 상태가 표시되지 않습니다.
검토 완료 여부를 서버에서 강제하는 로직도 아직 없습니다.

## 확인이 필요한 경우

- 연결 실패: 앱 실행 여부, 포트, `baseUrl` 확인
- 로그인 실패: dev 프로필과 초기 데이터 로그 확인
- 인증 실패: 로그인 요청을 다시 보내 토큰 갱신
- 학생 조회 실패: 초기 데이터 로그의 `studentId` 확인
- 제출 후 수정/검토 실패: 새 초안을 만들어 테스트

컬렉션의 테스트 스크립트는 각 단계의 예상 HTTP 상태 코드를 검사합니다.
