# 교사 AI 답변 초안 요청 제한 (선택적 기능)

## 배치와 식별 기준
- 외부 인증은 Spring Boot의 JWT 필터에서 수행하고, FastAPI는 같은 EC2의 loopback (`127.0.0.1:8000`)로 호출합니다.
- 따라서 FastAPI에서 원격 IP를 기준으로 호출량을 제한하면 서로 다른 사용자가 같은 IP로 묶일 수 있어 부적절합니다.
- 이 변경은 **Backend의 인증된 교사 사용자 ID**를 기준으로, `POST /teacher/complaints/{complaintId}/response/ai-draft` 엔드포인트에만 적용합니다.
- 다른 분석·요약·수정안 API에는 이번 제한을 적용하지 않습니다. 전체 AI 사용량 제한도 아닙니다.

## 안전한 기본값
기본값은 **비활성화**입니다. 별도 환경설정 변경 없이 기존 동작을 유지합니다.

```properties
# 선택적으로 운영 검토 및 배포 승인 후에만 활성화
teacher.ai-draft-rate-limit.enabled=${TEACHER_AI_DRAFT_RATE_LIMIT_ENABLED:false}
teacher.ai-draft-rate-limit.max-requests=${TEACHER_AI_DRAFT_RATE_LIMIT_MAX_REQUESTS:3}
teacher.ai-draft-rate-limit.window-seconds=${TEACHER_AI_DRAFT_RATE_LIMIT_WINDOW_SECONDS:60}
```

위 설정 예시는 **운영 설정을 수정하라는 지시가 아니며**, 코드의 `@Value` 기본값과 동일합니다.
활성화된 경우 사용자별 고정 60초 구간에 초안 생성 호출을 최대 3회 허용하고, 초과 시 Backend 공통 오류 형식의 HTTP **429**, `AI_DRAFT_RATE_LIMITED`, `Retry-After` 응답 헤더(초)를 반환합니다. 요청 거부 시 FastAPI LLM 호출 전에 종료됩니다.

## 제한 및 주의사항
- **단일 JVM 메모리 내 구현**입니다. 여러 Backend 인스턴스나 재시작 사이에 호출량은 공유되지 않으므로 분산 운영의 보장으로 사용하면 안 됩니다. 다중 인스턴스에서는 Redis/DB 기반 공유 카운터를 별도로 적용해야 합니다.
- 고정 윈도우 방식이므로 윈도우 경계에서 짧은 순간 허용량의 약 2배가 통과할 수 있습니다.
- 성공한 응답만 집계하지 않고 *요청 시도*를 차감합니다. 인증은 먼저 수행하지만, 대상 민원 접근 권한 및 서비스 정책 오류도 횟수에 포함될 수 있습니다. 이런 동작을 허용할지 운영 시 결정해야 합니다.
- 원문, JWT, API 키, 교사 이메일을 새 로그에 기록하지 않습니다.
- 사용자마다 카운터를 분리하지만, 개별 서버에 로컬 메모리로 저장됩니다. 만료된 카운터는 128번의 허용 요청마다 정리합니다.
- 위험 분석, 비동기 수정안 등 다른 생성 경로와 공통 비용 예산 정책은 별도 Backend 협의가 필요합니다.

## 검증
Backend 저장소에서:

```bash
./gradlew test --tests '*TeacherAiDraftRateLimiterTest' --tests '*TeacherResponsePolicyApiTest'
./gradlew test
```

오프라인 테스트는 비활성화 시 이전 동작 유지, 사용자 간 독립성, 429 및 `Retry-After`, 윈도우 만료, 병렬 요청 한도, AI 서비스 미호출을 확인합니다.
