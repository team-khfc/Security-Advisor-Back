# ADR 0004 — RESTful API 와 오류 모델

- 상태: 채택 (2026-11-07)
- 관련: AGENTS.md 5장, CONTRIBUTING.md 데이터 계약, docs/api/openapi.yaml

## 결정

1. 경로는 `/api/<복수형-kebab-case>` 자원 명사만. 소속 관계는 한 단계까지(`/api/security-requests/{id}/reviews`), 그 아래는 자체 ID(`/api/reviews/{id}`).
2. **상태 전이는 자원으로 표현**: 하위 자원 생성(POST `…/reviews`, `…/supplements`, `…/stage-returns`, `…/submissions`, `…/final-confirmations`, `…/ai-proposals`) 또는 단일 하위 자원 설정(PUT `…/allocation-completion`, `…/completion`, `…/closure`, `…/withdrawal`, `…/confirmation`, `…/application`, `…/revocation`). `status`·`stage` 를 직접 바꾸는 API 는 없다. 업무 상태가 아닌 값(알림 읽음)만 PATCH.
3. **쓰기 요청은 `expectedRevision`** 을 받는다(처음 채우는 항목·자리 표시 제외). 중복이 위험한 명령(제출)은 `Idempotency-Key` 헤더로 멱등. 비동기 접수는 202 + `Location`.
4. **오류는 ProblemDetail**: 400(형식·검증) · 401 · 403(권한) · 404(없음·남의 자원) · 409(revision·중복) · 422(업무 규칙) · 429(로그인 잠금) · 503(외부 의존 불가, AI). 모든 응답·오류에 `X-Request-Id`/`requestId`.
5. **응답은 dto 의 record** 만, 엔티티 노출 금지. 목록은 배열 또는 `PageResponse(content·page·size·totalElements)`.
6. **계약은 OpenAPI 파일**(`docs/api/openapi.yaml`, springdoc 추출)로 관리하고 Back 이 소유한다. 계약 변경은 Back PR 이 먼저다.
