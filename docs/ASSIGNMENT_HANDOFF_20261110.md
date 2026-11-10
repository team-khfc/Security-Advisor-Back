# 담당자 변경 후 재배정·업무 인계

2026-11-10. Back main `05a329e` 기준. F-11/F-12 보완이며 계정 역할, 분야 등록, 신청 배정은 별개로 유지한다.

## 구현

- 역할 변경·계정 비활성·분야 등록 누락/비활성을 조회할 때 판정한다. 기존 배정 행을 삭제하거나 자동 이동하지 않는다.
- `AssignmentResponse.reassignmentReason`, 목록 `overview.fields[].reassignmentReason`, 관리자 `todo-summary.unassignedRequests`가 같은 인계 대상을 표시한다. 필요한 검토 분야만 확인하며 2~4단계 진행 중 신청을 포함하고 확정·종결·철회는 제외한다.
- `ReviewerPoolResponse`에 `eligible`, `unavailableReason`을 추가한다. 역할이 다르거나 계정이 비활성이면 기본 담당자로 다시 지정할 수 없다.
- 같은 사업의 기존 담당자가 부적격이면 유효한 기본 담당자를 다음 후보로 사용한다. 유효 후보가 없으면 미배정 상태를 유지한다.
- 관리자는 등록된 활성 담당자에게 사유를 남겨 재배정한다. 전임 배정은 비활성 이력으로 보존하고 답변·협의·제출본은 재배정 자체로 변경하지 않는다. 종료된 신청의 재배정은 422로 거부한다.
- 계정 변경에 따른 기존 세션 401, 현재 역할의 활성 배정에 한정한 조회 권한은 P0 정책을 유지한다. 분야 등록 비활성은 계정 비활성과 다르며, 기존 배정을 자동 해제하지 않는다.

| 조회 사유 | 의미 |
|---|---|
| UNASSIGNED | 필요한 분야의 담당자 미배정 |
| ACCOUNT_INACTIVE | 담당 계정 비활성 또는 계정 없음 |
| ROLE_CHANGED | 현재 계정 역할과 배정 분야 불일치 |
| NOT_REGISTERED | 해당 분야 담당자 등록 없음 |
| POOL_INACTIVE | 해당 분야 담당자 등록 비활성 |

API 계약은 `docs/api/openapi.yaml`을 재생성했다. DB 마이그레이션·의존성·실제 장비 적용 변경은 없다.

## 검증

- `spotlessApply check`: 173개 중 171개 통과, 실패 0. 별도 실행하는 OpenAPI 내보내기와 외부 LLM 실호출 2개는 기본 검사에서 조건부 제외된다. OpenAPI 내보내기는 별도 실행 성공.
- 신규 통합 검사 4개: 역할 변경과 세션 차단/인계 기록 보존, 계정·등록 비활성/종료 보호, 등록 누락의 2·3·4단계 일치, 기존 사업 담당자 부적격 시 기본 담당자 대체.
- Docker `securityadvisor-integration`, Front `localhost:3001`, 실제 PostgreSQL 사용. 합성 계정 7개(`handoff-40f5e73a-*`), 신청 261~264. 준비 실행의 합성 계정·신청도 삭제하지 않고 보존했다.
- HTTP 자동 확인 22개 + 브라우저 저장 결과/최종 상태 확인 5개 = 27개 통과. 전임 세션 401, 전임자 재로그인 상세 404, 신규 담당자의 배정 전 404/배정 후 조회, 이력·기존 답변 보존, 부적격 기본 지정 422, 신규 자동 배정을 확인했다.
- 브라우저: 관리자 로그인 → 신규 ICT인프라 담당자 등록·기본 지정 → 전임자 역할 변경/정보보안 계정 비활성 → 인계 목록/상세 사유 → 양 분야 재배정 → 신규 담당자 로그인 → 기존 답변 수정·저장.
- 분야 등록 비활성/재활성, 빈 사유 저장 차단, Tab·Enter·Escape 조작을 확인했다. 기본 담당자는 원래 `infra`/`security`로 복구했고, 합성 신청 4건은 인계 필요가 해소되었다.
- 실제 장비·외부 AI·외부 알림은 호출하지 않았으며 기존 DB/파일을 보존했다.

재현 도구: `tools/verify_assignment_handoff.mjs`. `DEMO_ACCOUNT_PASSWORD`를 환경변수로 주고 실행한다. `VERIFY_ORIGIN`, `VERIFY_ADMIN_USERNAME`, `SYNTHETIC_ACCOUNT_PASSWORD`, `VERIFY_OUTPUT`을 선택적으로 지정할 수 있다. 합성 계정과 신청을 만들므로 시연/검증 환경에서 사용한다. 출력에는 비밀번호·세션을 기록하지 않는다.

관련 이슈: [Back #13](https://github.com/team-khfc/Security-Advisor-Back/issues/13), [Front #46](https://github.com/team-khfc/Security-Advisor-Front/issues/46).
