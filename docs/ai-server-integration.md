# AI 서버 연동·배포 확인

## 배치 구조

Spring Boot와 FastAPI는 같은 AWS EC2에서 실행한다. FastAPI는 외부 포트로 노출하지 않고
`127.0.0.1:8000`에서만 수신하므로 Spring Boot의 운영 호출 주소는
`http://localhost:8000`이다.

```text
Spring Boot :8080
  └─ HTTP http://localhost:8000/api/v1/ai/*
       └─ teacherhub-ai.service (FastAPI)
```

AI 서버 운영 경로:

- 소스: `/opt/teacherhub-ai/app-source`
- 가상환경: `/opt/teacherhub-ai/.venv`
- 환경변수: `/opt/teacherhub-ai/.env`
- systemd: `teacherhub-ai.service`
- 저장소/브랜치: `ai-sangsang-challenge-team4/AI`, `dev`

AI 저장소의 `.env`와 API 키는 백엔드 저장소에 복사하거나 커밋하지 않는다.

## 백엔드 설정

```properties
AI_SERVER_URL=http://localhost:8000
AI_SERVER_CONNECT_TIMEOUT_MS=2000
AI_SERVER_REQUEST_TIMEOUT_MS=60000
AI_RISK_ANALYSIS_TIMEOUT_MS=60000
AI_REVISION_TIMEOUT_MS=30000
```

같은 EC2 배치를 유지하는 한 `localhost`가 올바르다. 서비스를 다른 인스턴스로 분리할 때만
사설 DNS 또는 사설 IP로 `AI_SERVER_URL`을 변경하고, 보안 그룹과 전송 암호화를 함께 검토한다.
위험 분석 제한 시간 기본값은 60초이고 수정안은 30초다. 제한 시간 조정은 속도 개선이 아니며,
수정안 호출은 위험 분석 POST 응답 경로와 분리되어 있다.

## 직접 확인 순서

배포 후 EC2 안에서 다음 순서로 확인한다. 응답 본문에 실제 민원이나 인증 정보를 사용하지 않는다.

```bash
sudo systemctl is-active teacherhub-ai.service
curl --fail --silent http://localhost:8000/
sudo journalctl -u teacherhub-ai.service --since "10 minutes ago" --no-pager
```

그다음 테스트 계정과 비식별 입력으로 Spring Boot의 위험 분석 API를 호출해 다음을 확인한다.

FastAPI 네 경로의 실제 모델 호출은 EC2의 백엔드 소스 디렉터리에서 아래 스크립트로 먼저
검증할 수 있다. 이 스크립트는 비식별 고정 입력만 사용하며 응답 본문을 출력하지 않는다.

```bash
bash scripts/verify-ai-live.sh
```

동일한 네 경로를 Spring Boot의 실제 Python 연동 클래스까지 통과시켜 확인하려면 EC2의 백엔드
소스 체크아웃에서 수동 live 테스트를 실행한다. 이 태그는 기본 `test`와 CI에서 제외되며 실제
Claude·Gemini 호출 비용이 발생한다.

```bash
AI_SERVER_URL=http://localhost:8000 ./gradlew liveAiTest
```

1. FastAPI `/api/v1/ai/analyze` 호출과 Spring Boot HTTP 200
2. `analysisMode=HYBRID`, `aiAnalysisStatus=COMPLETED`
3. 분석·태그·Severity·안전 신호의 DB 저장과 조회
4. 위험 분석 응답 후 `revisionStatus=PENDING`, 이후 수정안 `/api/v1/ai/revision` 결과 저장과 `COMPLETED` 전환
5. 교사 대화 완충 요약 `/api/v1/ai/summary`
6. Low·Medium 교사 답변 초안 `/api/v1/ai/response-draft`

위험 분석 AI가 중단되면 Rule-only로 전환한다. 위험 분석은 성공하고 수정안 AI만 실패하면
HYBRID 결과를 유지하고 `revisionStatus=UNAVAILABLE`로 전환한다. 완충 요약은
`summaryStatus=UNAVAILABLE`, 답변 초안은 503이 되는지 확인한다. 이 세 동작이 롤백 기준과
장애 알림의 기준이다.

## 배포 후 관측과 롤백

- 확인 지표: capability·attempt별 AI 호출 성공률과 durationMs, Rule-only 전환율,
  수정안 UNAVAILABLE 비율, 429·5xx·timeout, p95 지연, EC2 가용 메모리
- 비용 지표: Claude, Gemini, Cloudflare Workers AI 호출량
- 데이터 지표: ChromaDB 백업 성공과 최근 복구 시험 시각
- 롤백 조건: Spring Boot 오류율 급증, 지속적인 AI timeout, 메모리 부족 재시작, 잘못된 스키마 응답
- 롤백 방법: 백엔드 배포를 이전 버전으로 되돌리고 AI 장애 중에는 서버의 기존 Rule-only 정책을 유지한다.
