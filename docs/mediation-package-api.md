# 대응 기록 패키지 API

## 업무 규칙

담당 교사는 제출된 민원의 허용된 원문·마스킹본·분석·수정·대응 이력을 생성 시점의 불변 JSON 스냅샷으로 만들고, 같은 권한 검사를 거쳐 조회하거나 다운로드할 수 있다.

## 안전 및 데이터 정책

- 기본 생성값은 원문 미포함이다. `includeOriginalMessages=true`를 명시한 경우에만 전송된 메시지 원문이 패키지에 들어간다.
- 마스킹본은 패키지 생성 시 서버의 현재 개인정보 마스킹 규칙으로 다시 생성한다.
- 분석 근거, 수정안, 검토 사유, 이력 사유처럼 자유 텍스트인 필드도 같은 마스킹 규칙을 적용한다.
- 사용자 이름과 민원 제목은 패키지에 넣지 않는다. 메시지 작성자는 역할로만 표시한다.
- 생성된 스냅샷은 수정하지 않는다. 조회와 다운로드는 저장된 같은 JSON을 사용한다.
- 생성 행위는 `MEDIATION_PACKAGE_CREATED` 감사 이벤트로 기록한다.
- 응답에는 `Cache-Control: no-store`를 적용한다.

## 생성

`POST /teacher/complaints/{complaintId}/mediation-packages`

요청 헤더:

```http
Idempotency-Key: complaint-42-package-1
Content-Type: application/json
```

요청 본문:

```json
{
  "includeOriginalMessages": false
}
```

- 담당 교사가 아닌 경우 또는 민원이 `DRAFT`인 경우 `403`을 반환한다.
- 같은 민원·생성자·멱등 키의 재요청은 기존 패키지를 반환한다.
- 같은 멱등 키의 원문 포함 조건이 달라지면 `409 MEDIATION_PACKAGE_CONFLICT`를 반환한다.
- 성공 시 `201`과 패키지 메타데이터 및 전체 스냅샷을 반환한다.

## 조회

`GET /teacher/complaints/{complaintId}/mediation-packages/{packageId}`

- 담당 교사 권한을 다시 확인한다.
- 패키지가 없거나 다른 민원에 속하면 `404 MEDIATION_PACKAGE_NOT_FOUND`를 반환한다.
- 성공 시 `200`과 생성 당시 스냅샷을 반환한다.

## 다운로드

`GET /teacher/complaints/{complaintId}/mediation-packages/{packageId}/download`

- `Content-Type: application/json`과 첨부 파일 `Content-Disposition`을 반환한다.
- `X-Content-SHA256` 헤더는 저장된 JSON 바이트의 SHA-256 값이다.
- 조회 API의 `content`와 같은 생성 시점 데이터를 다운로드한다.

## 패키지 구성

- 민원: ID와 생성 시점 상태
- 메시지: 작성자 역할, 유형, 상태, 버전, 전송·읽음 시각, 마스킹본, 선택적 원문
- 분석: 점수·등급·방식·모델 메타데이터·Safety Flag·감지 태그
- 수정: 수정 상태·수정 사유·전체 수정안·태그별 수정 제안
- 검토: 교사 검토 전후 점수·등급·방향·사유·시각
- 대응 이력: 감사 이벤트와 누락 검사 결과(사용자 이름 제외)

## 운영 확인 및 롤백

- 배포 후 패키지 생성 성공률, `MEDIATION_PACKAGE_CONFLICT`, `MEDIATION_PACKAGE_NOT_FOUND`, 평균 스냅샷 크기를 확인한다.
- 예상하지 못한 원문 노출 또는 권한 우회가 확인되면 생성·조회·다운로드 라우트를 즉시 차단하고, 해당 배포를 롤백한다.
- DB 롤백이 필요하면 신규 API 사용을 먼저 중지한 뒤 `mediation_packages` 보존·백업 정책을 결정한다. 개인정보가 포함될 수 있으므로 자동 삭제하지 않는다.
