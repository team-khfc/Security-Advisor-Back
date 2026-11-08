# ADR 0001 — 기술 스택과 DB 스키마 규칙

- 상태: 채택 (2026-11-07, 2026-11-08 보강)
- 관련: AGENTS.md 1장·5장, FUNCTIONAL_SPEC 8-1, docs/DEVIATIONS.md #2·#3

## 결정

1. **스택**: Java 25(LTS) · Spring Boot 4.1 · Gradle(Kotlin DSL) · Spring Data JPA/Hibernate · PostgreSQL 17 · Flyway · Spring AI 2.0(어댑터 뒤) · Docker Compose. Lombok 은 허용 목록(`@Getter`·`@RequiredArgsConstructor`·`@NoArgsConstructor(PROTECTED)`·`@Slf4j`)만, `lombok.config` 로 강제.
2. **스키마 변경은 Flyway 마이그레이션만**(`V<연월일시분>__desc.sql`). JPA 는 `ddl-auto: validate`. main 에 머지된 파일은 수정하지 않는다.
3. **외래 키 제약을 두지 않는다**. 참조 무결성은 도메인·서비스가 지키고 참조 컬럼에는 조회 인덱스를 둔다. 업무 불변식을 담은 CHECK·UNIQUE(단계×상태, 사업 건당 서류 유형 1건, 제출본 멱등성 키, 종료일-또는-재검토일 등)는 유지한다.
4. **이름 규칙**: 테이블 복수형 snake_case, 기본 키는 `<단수형>_id`(맨 `id` 금지), 같은 이름의 컬럼은 모든 테이블에서 같은 의미·형식. `members` 의 PK 는 `username`, 참조 컬럼은 `_username` 접미사.
5. **반구조 데이터는 jsonb**(필드 스키마·스냅샷·체크리스트·대상 항목 키 등). 엔티티는 JSON 문자열(`@JdbcTypeCode(SqlTypes.JSON)`)만 들고 해석은 service 가 한다. 정수 단계 컬럼은 SMALLINT(`@JdbcTypeCode(SqlTypes.SMALLINT)`).
6. **낙관적 잠금**: 변경 가능한 애그리거트는 `revision`(`@Version`)을 가지며 쓰기 API 는 `expectedRevision` 을 받아 불일치 시 409.
7. **감사 이력은 추가 전용**(DB 트리거가 UPDATE·DELETE 거부).

## 근거

- 시연·운영 단순화와 성능(팀 결정). FK 가 없어도 부분 유일 인덱스·CHECK 로 핵심 불변식은 DB 가 지킨다.
- 서류 유형·질문을 데이터로 두는 설계(ADR 0005)에는 jsonb 가 맞다.
