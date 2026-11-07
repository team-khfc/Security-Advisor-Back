# 지침 이탈·추가 결정 기록 (규칙 문서 개정 대기)

이 저장소의 구현이 팀 규칙 문서(PROJECT_CONTEXT·FUNCTIONAL_SPEC·AGENTS 등)와 **의도적으로 다른 부분**과, 규칙 문서에 **아직 없는 결정**을 적어 둔다. 규칙 문서 개정(PROJECT_RULES 7-1 절차)이 끝나면 해당 항목을 지운다. 이 세션의 작성 계정은 팀 Claude 프로젝트 문서 쓰기 권한이 없어 여기 먼저 기록했다.

| # | 내용 | 다른 규칙 문서 | 반영할 곳 | 결정·근거 |
|---|---|---|---|---|
| 1 | **서류 유형을 게시본 데이터(`docTypes`)로 정의** — `DocType` enum 제거, `DocTypeCode` 문자열 값 객체, `security_requests.doc_type` CHECK 없음. 운영자가 코드 배포 없이 서류를 추가할 수 있는 구조 | FUNCTIONAL_SPEC 2장 용어표 "DocType: FIREWALL / SECURITY_REVIEW"(enum 전제), 8-1 `security_requests.doc_type`; PROJECT_RULES 4장 "11.11까지 새 기능 추가 금지"(F-08 성격의 구조 선반영); INSTRUCTIONS "1차 범위 서류 2종 고정" | FUNCTIONAL_SPEC 2장·8-1, PROJECT_CONTEXT 7장 확정 결정, AGENTS 5장 유비쿼터스 언어(DocTypeCode) | 2026-11-07 팀장 승인. 추후 변경 비용이 너무 커 동결 전 구조만 선반영. 상세 ADR 0005. 1차 범위 2종은 seed 데이터로 유지 |
| 2 | **외래 키 제약 미사용** — 참조 무결성은 도메인·서비스, 참조 컬럼은 인덱스만 | 규칙 문서에 명시 없음(8-1은 "FK" 표기로 제안) | FUNCTIONAL_SPEC 8-1 제안 표 주석, ADR 0001(스택·스키마) | 2026-11-07 팀장 결정(성능·운영 단순화). 업무 불변식 CHECK·UNIQUE 는 유지 |
| 3 | **기본 키 컬럼 이름 규칙** — 맨 `id` 금지, `<테이블 단수형>_id`, 같은 이름의 컬럼은 모든 테이블에서 같은 의미·형식 | AGENTS 5장 이름 규칙 표(테이블·컬럼 행)에 없음 | AGENTS 5장 이름 규칙 표 | 2026-11-07 메인 개발자 결정. README DB 명세에 기재 |
| 4 | **답변(`answers`)을 별도 리포지토리로 관리** — 사업 건·신청 애그리거트의 컬렉션으로 매핑하지 않고 ID 로 묶음 | AGENTS 5장 "리포지토리는 애그리거트 루트 단위로만" | ADR 0001 또는 AGENTS 5장 예외 명시 | 한 테이블(`answers`)을 두 범위(CASE/REQUEST)가 쓰는 구조에서 양쪽 JPA 컬렉션 매핑 위험을 피하기 위한 실용적 예외. 답변 규칙은 `Answer` 도메인 메서드가 지킨다 |
| 5 | **처리 기한(Deadline) 엔티티·서비스를 `request` 컨텍스트에 둠** — 명세는 "별도 컨텍스트 없음, 정책은 configuration·영업일 계산은 common"만 정함 | FUNCTIONAL_SPEC 1장(기한 컨텍스트 위치 미지정) | FUNCTIONAL_SPEC 1장 도메인 맵에 "Deadline 엔티티는 request" 명시 | 기한은 신청 단위 자원이라 request 에 두는 것이 ID 참조 규칙과 맞음. 정책(DeadlinePolicy)은 게시본, 계산(BusinessCalendar)은 common — 명세대로 |
| 6 | **감사 이력 대상 단위** — 배정·기한 이벤트도 `target_type=SECURITY_REQUEST` 로 기록(배정·기한 ID 는 summary 에) | FUNCTIONAL_SPEC 3-3장 기록 항목(대상 유형·ID) | 해석 명시 | 신청의 "처리 이력" 탭이 한 번의 조회로 완성되도록. 답변 변경은 값 없이 항목 키·출처만 기록(기관 사실 값 보호, 불변식 25 확장 해석) |
| 8 | **담당자 입력·확인을 review 컨텍스트 API(`/api/reviews/{id}/answers/{fieldKey}`)로 노출** — 저장은 request 컨텍스트 `AnswerService.inputByStaff/confirmByStaff` 가 하고, 배정·단계 검사는 review 가 먼저 한다 | FUNCTIONAL_SPEC 1장 도메인 맵(답변은 request, 검토는 review — 담당자 입력의 경로 미지정) | FUNCTIONAL_SPEC 1장·AGENTS 5장 API 예시 | "검토 행위는 배정자만" 규칙을 한곳(review.ReviewerAccessPolicy)에서 강제하기 위함. 값·출처는 여전히 `Answer` 도메인 메서드가 지킨다 |
| 9 | **답변 변경 → 협의 재검토 연결은 Spring `ApplicationEvent`(동기, 같은 트랜잭션)** — request 가 `AnswerChangedEvent` 를 내고 review 가 `@EventListener` 로 받음 | AGENTS 5장 "컨텍스트 간 ID + 공개 서비스로 연결"(이벤트 언급 없음), PROJECT_CONTEXT "이벤트 소싱·CQRS 미도입" | AGENTS 5장에 "같은 트랜잭션의 동기 ApplicationEvent 허용" 명시 | request → review 의존을 만들지 않고 불변식 7(공통 정보 변경 시 미제출 신청 재검토)을 구현하는 최소 수단. 이벤트 소싱이 아니다(저장하지 않음) |
| 10 | **차수 반환·종결 권한** — 그 신청에 활성 배정된 담당자(어느 분야든). 관리자(ADMIN)는 할 수 없음 | FUNCTIONAL_SPEC F-05 "담당자" (배정 요건 명시 없음) | FUNCTIONAL_SPEC F-05·3-1장 배정 불변식 | "검토 행위는 배정자만"의 일관된 적용. 관리자가 필요하면 재배정 후 처리 |
| 7 | **Java 버전** — 구축본은 Java 25 toolchain(지침과 일치). 한때 21로 바꾸자는 안이 있었으나 폐기 | — | — | 기록용. 지침(Java 25) 그대로 |

## 규칙 문서 개정 시 확인할 것

- FUNCTIONAL_SPEC 2장: `서류 유형 | DocTypeCode | 게시본 docTypes 가 정의하는 코드(1차 seed: FIREWALL, SECURITY_REVIEW)` 로 교체, 8-1 `publications` 행에 "docTypes 포함", `security_requests.doc_type` CHECK 문구 삭제.
- PROJECT_CONTEXT 7장: "서류 유형 데이터화(ADR 0005)" 결정 행 추가, 8장 후속에 "F-08 유형·질문 편집 화면, 양식 템플릿 데이터화(11.11 이후)".
- AGENTS 5장: 이름 규칙 표 보강(#3), 유비쿼터스 언어에 DocTypeCode.
- 관련 공유 문서("기능 명세 보고서")에 구버전 표시 또는 갱신.
