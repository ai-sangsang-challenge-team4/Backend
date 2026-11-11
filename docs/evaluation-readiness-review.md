# TeacherHub 심사·안전 구현 점검

- 점검일: 2026-11-10
- 점검 기준: `심사평가 안내.pdf`, `AGENTS.md`, `CORE-FEATURE-SPEC.md`,
  `docs/AI-QUALITY-PLAYBOOK.md`, `docs/risk-api.md`, `docs/risk-analysis-erd.md`, 실제 코드와 테스트
- 범위: 평가 방식, AI와 결정론적 가드레일 경계, AI/내부 장애 분기, 위험도별 기능,
  백엔드 권한·상태·동시성·예외 처리

첨부 PDF는 제품 요구사항이 아니라 **심사 기준**으로만 사용했다. 제품 동작은 기능 명세와
API 문서, 코드의 일치 여부를 기준으로 판단했다.

## 1. 심사는 어떻게 이루어지는가

| 평가 영역 | 배점 | TeacherHub에서 보여줘야 할 증거 |
|---|---:|---|
| 사업성 | 30 | 학교 민원 문제 정의, 실제 사용자 피드백, 도입·확산 경로 |
| 기술성 | 55 | AI와 자체 Rule/DB 역할 분리, 조건 분기, 상태 관리, 실패 복구, 실데이터·경계값 시연, 정답 데이터와 지표 |
| 태도·책임 | 15 | 팀 설명 가능성, 개인정보 동의·마스킹·보관/삭제 정책, 과장 없는 근거 제시, 안전 대응 |

기술성 안에서는 기술 구현 20점, 시연·운영 15점, 검증·신뢰성 20점이다. 단순 LLM 호출
래퍼보다 자체 규칙·DB와 LLM의 역할이 분리되고, 조건부 분기·상태·실패 처리·사람 개입이
있는 구조가 높은 평가를 받는다. 시연은 평가자가 입력한 실제 데이터, 빈값·긴 입력·데이터 없음
같은 경계값, 안전선 입력을 다룰 수 있어야 한다. 실패 자체보다 원인 진단과 복구 능력이 중요하다.

검증은 시연과 별도다. 독립적인 정답 데이터, 지표·통과 기준, 실제 사용자 테스트와 실패 사례가
필요하다. 근거 없는 성능 수치, 동의 없는 민감정보, 설명할 수 없는 코드, 실행되지 않는 데모는
감점 위험이 크다.

## 2. AI와 가드레일의 경계

| 판단/처리 | 담당 | 현재 상태 |
|---|---|---|
| 인증·담당 교사/작성자 권한 | 서버 정책·Security | 구현 |
| 민원/메시지 상태 전이 | Entity·Service | 구현 |
| 개인정보 마스킹 | `PiiMaskingService` | 지원 패턴에 한해 구현 |
| Rule 사전 탐지 | `DictionaryRuleBasedRiskDetector` | CSV 72행 기반 구현 |
| 반복 민원 임계값 | 서버 Rule | 7일·3건, 문맥 크기 제한 구현 |
| 모호한 위협·부당 요구의 문맥 판단 | 외부 LLM | 인터페이스와 Python 연동 구현 |
| 태그 병합·중복 방지·점수·등급 | `RiskEvaluator` | 구현 |
| `PRESENT`인데 근거가 없는 AI 결과 | 서버 검증 | `BOUNDARY`로 하향 구현 |
| Emergency hard guardrail | 서버 승인 Rule | **미구현** |
| Emergency/safety lock의 답변 차단 | 서버 정책 | 구현 |
| Emergency 하향·잠금 해제 | 지정 관리자 | 구현 |
| 답변 초안·완충 요약 | `TeacherSupportContentProvider` | 경계는 구현, 실제 제공자는 Mock |

핵심 원칙은 “LLM은 제안하고 서버가 검증·결정한다”이다. 외부 AI 결과는 enum, 중복 태그,
confidence, 모델 정보, 근거 개수·길이·좌표를 검증한 뒤에만 점수 계산과 저장에 사용한다.
위치가 있는 근거는 실제 마스킹 본문의 같은 구간과 일치해야 한다.

## 3. 장애 분기: 현재 반영된 동작

| 실패 지점 | 분류 | 일반 AI 분석 | 최종 제출 중 분석 | HTTP |
|---|---|---|---|---:|
| AI 연결·timeout·429·5xx | 외부 AI 장애 | Rule-only 잠정 결과 | Rule-only 결과로 제출 계속 | 위험 분석 200 / 일반 AI 503 |
| AI JSON 파싱·스키마·허용값 오류 | 신뢰 불가 AI 출력 | 1회 재생성 후 Rule-only | 1회 재생성 후 Rule-only 제출 | 위험 분석 200 / 일반 AI 502 |
| 수정안 생성 AI 연결·응답 검증 실패 | 외부 AI 장애/신뢰 불가 출력 | 수정안 없이 Rule-only 저장 | 제출 분석에서는 수정안 미생성 | 위험 분석 200 |
| 개인정보 마스킹 실패 | 내부 개인정보 가드레일 | 실패 | **제출 중단** | 500 `PRIVACY_MASKING_FAILED` |
| Rule/반복 문맥 처리 실패 | 내부 결정론적 가드레일 | 실패 | **제출 중단** | 500 `RISK_RULE_PROCESSING_FAILED` |
| 점수·등급 계산 실패 | 내부 결정론적 가드레일 | 실패 | **제출 중단** | 500 `RISK_SCORE_CALCULATION_FAILED` |
| 분석·태그·감사 이벤트 저장 실패 | 내부 저장 경계 | 실패 | **제출 중단·롤백** | 500 `RISK_ANALYSIS_SAVE_FAILED` |
| AI 요청 직렬화·URI·4xx 구성 오류 | 내부 연동 오류 | 실패 | **제출 중단** | 500 |
| 권한·상태·버전·저장 실패 | 업무/백엔드 오류 | 해당 오류로 실패 | 롤백 | 4xx/5xx |

반영 사항:

- `AiServiceUnavailableException`, `AiResponseInvalidException`,
  `AiRequestPreparationException`, `InternalSystemException`을 분리했다.
- Python 연동에 연결/요청 timeout을 추가했다.
- AI 응답 body와 원문을 예외 메시지·표준 출력에 남기지 않는다.
- 외부 AI 장애와 1회 재생성 후에도 불량인 출력은 Rule-only 잠정 분석으로 저장하고,
  `analysisMode`, 수동 검토 안내와 별도 감사 이벤트를 반환한다.
- 수정안 생성 AI 장애도 미완료 하이브리드 결과를 저장하지 않고 Rule-only 결과로 전환한다.
- 개인정보 마스킹, Rule, 점수 설정 같은 내부 실패를 AI 장애로 오인해 제출하던 경로를 막았다.
- 교사 AI 초안의 전체 대화와 완충 요약 입력도 먼저 마스킹한다.
- 교사 AI 출력은 형식·길이를 검증하고 다시 마스킹한다.

완충 요약 실패 시 원문 자동 노출을 하지 않도록 변경했다. 안내 상태를 반환하고 교사가 별도
원문 열람 API를 사용해야 하며 해당 열람은 감사 이벤트로 기록한다.

## 4. 위험도별 기능 대조

| 위험도 | 대화 기본 표시 | `allowedActions` | 답변 기능 | 실제 구현 일치 여부 |
|---|---|---|---|---|
| LOW 0~20 | 완충 요약 | 없음 | RAG 초안·저장·전송 | 요약 분기 구현, 실제 RAG 미구현 |
| MEDIUM 21~40 | 완충 요약 | 없음 | AI 초안·저장·전송 가능 | 명세와 일치 |
| HIGH 41~60 | 완충 요약 | 템플릿, 증거 패키지, 관리자 검토 | 생성형 AI 금지, 채택 템플릿만 저장·전송 | **부분 구현** |
| EMERGENCY 61~100 | 완충 요약 | 증거 패키지, 관리자 검토, 공식 절차 | AI 초안·저장·전송·완료 차단 | 차단·관리자 공유/재검토 구현, 일부 행동 미구현 |
| 분석 없음 | 원문 | 수동 검토 요청 | 대화 조회 가능 | 행동 표시만 구현 |

세부 불일치:

1. HIGH 템플릿 조회·채택은 구현되어 있다.
2. `CREATE_EVIDENCE_PACKAGE`, `VIEW_OFFICIAL_PROCEDURE`, `REQUEST_MANUAL_REVIEW`는 enum과
   화면용 행동 값만 있고 실행 API가 없다.
3. HIGH에 `REQUEST_ADMIN_REVIEW`가 표시되지만 현재 관리자 공유 API는 EMERGENCY만 허용한다.
   UI가 실행 불가능한 행동을 노출할 수 있다.
4. EMERGENCY 차단은 유효 위험도와 safety lock을 모두 확인하고 차단 감사 이벤트를 남긴다.
5. Emergency 하향 승인과 잠금 해제는 같은 트랜잭션에서 처리한다.

## 5. 현재 구현된 백엔드 예외·분기

- Bearer 인증과 역할/소유권/담당 교사 검증
- DRAFT/OPEN/CLOSED 및 `waitingFor` 기반 상태 전이
- 민원 행 비관적 잠금, 관리자 공유 버전 충돌 처리
- 교사 최종 답변 `Idempotency-Key` 중복 방지
- 같은 내용 hash 분석 재사용과 성공 분석 최대 2회 제한
- Bean Validation 기반 제목·답변·사유·점수·페이지 범위 검증
- 업무 예외의 공통 `ErrorResponse` 변환
- 위험 점수 `min(100, 태그별 max(Rule, LLM) 합계)`와 LOW/MEDIUM/HIGH/EMERGENCY 경계
- 관리자 공유·검토·원문 열람·AI 초안 생성·안전 차단 감사 이벤트
- 내부 데이터 누락과 잘못된 업무 상태를 각각 500과 409로 구분

## 6. 구현되지 않았거나 추가 결정이 필요한 부분

### P0 — 심사/안전 전에 반드시 결정

1. **Emergency hard guardrail 미구현**
   - 현재 사전의 위협 키워드는 문맥 확인이 필요한 후보라 즉시 Emergency로 승격할 수 없다.
   - 승인된 rule ID·버전, 대상/행동/의도, 인용·부정·관용어 예외, 근거 요구사항이 필요하다.
   - `baseRiskScore/Level`, `effectiveRiskScore/Level`, `guardrailDecision`, 발동 rule 감사 저장도 없다.
2. **hard guardrail 자체 장애 정책 미확정**
   - 명세의 기본안은 `REVIEW_REQUIRED + safetyLock`이지만 제출 허용 범위와 운영 알림이 확정되지 않았다.
3. **운영 DB baseline 선행 필요**
   - Severity·안전 신호 증분 migration은 추가했지만 전체 초기 migration은 없다. 운영 적용 전 기존 DB baseline 등록이 필요하다.

### P1 — 높은 기술성·검증 점수를 위해 필요

1. 스키마 오류 1회 재생성은 구현됐지만 연결 장애 retry, circuit breaker와 Rule-only 전환 지표가 없다.
2. 골든셋 데이터, 독립 라벨 결과, 평가 runner, confusion matrix, 기준선 비교 결과가 없다.
3. prompt/dataset 버전과 AI 처리 시간·성공/실패·사용자 수정 결과를 저장/관측하지 않는다.
4. 실제 교사 초안·완충 요약 제공자가 없고 Mock 구현만 있다.
5. 생성 수정안의 전체 리스트 크기·각 필드 최대 길이 검증은 아직 부분적이다.
6. 위험도별 `allowedActions`와 실제 실행 API가 일치하지 않는다.
7. 개인정보 보관 기간·삭제 절차의 실제 API/배치/운영 증거가 확인되지 않는다.
8. CI에서 개인정보 누출 0건, Emergency 차단 회귀 0건, 불량 enum 0건을 별도 배포 게이트로 집계하지 않는다.

### P2 — 시연 안정성과 운영 완성도

1. AI 서버 장애용 로컬 backup/mock 전환 절차와 시연 runbook이 없다.
2. 빈 입력·매우 긴 입력·데이터 없음·AI timeout·불량 JSON을 한 번에 재현하는 시연 시나리오가 없다.
3. AI 장애율, fallback 비율, `BOUNDARY/UNKNOWN`, 관리자 override, p95 지연 알림이 없다.

## 7. 평가 대비 권장 순서

1. Emergency hard guardrail과 장애 시 제출/잠금 정책을 사람 승인으로 확정한다.
2. 위험도별 `allowedActions`를 실제 API와 맞춘다.
3. 비식별 골든셋 50건, 독립 2인 라벨, 평가 runner와 기준선 리포트를 만든다.
4. 연결 장애 retry/circuit breaker/운영 지표와 로컬 시연 backup을 추가한다.
5. DB 연결 전 Flyway baseline과 Severity·안전 신호 컬럼의 보관·삭제 절차를 검증한다.

## 8. 배포 후 지표와 롤백 조건

- 외부 AI 전송/출력 개인정보 원문 1건 이상: 즉시 롤백 및 AI 경계 차단
- 인증·권한·Emergency 차단 회귀 1건 이상: 즉시 롤백
- 파싱 불가·허용되지 않은 enum 저장 1건 이상: 즉시 롤백
- `THREAT`/`PRIVACY` recall이 승인 기준선보다 하락: 배포 보류 또는 이전 버전 복귀
- AI 실패/fallback 비율과 p95 지연이 합의 임계값 초과: AI 기능 축소 또는 이전 adapter로 복귀

## 9. 작업 회고

```text
의도: 외부 AI 장애와 내부 안전 가드레일 실패를 분리하고 평가 기준 대비 구현 공백을 확인한다.
검증: 예외 매핑·마스킹 경계·출력 검증·위험도 분기 테스트를 추가하고 전체 테스트를 실행한다.
놓친 점: 초기 확인에서 git 추적 파일만 보고 로컬 AI-QUALITY-PLAYBOOK.md의 존재를 잘못 판단했다.
다음 규칙: 명세 존재 여부는 git 추적 목록과 실제 파일시스템을 함께 확인한다.
```

`docs/AI-QUALITY-PLAYBOOK.md`는 현재 로컬에 존재하며 이번 점검의 기준으로 사용했다. 다만
`.gitignore`에 포함되어 있어 다른 clone과 CI에는 자동으로 전달되지 않는다. 삭제된 것은 아니지만,
팀 공통 품질 기준으로 사용할 경우 추적 여부를 별도로 결정해야 한다.
