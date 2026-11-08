# ADR 0003 — DDD 구조와 컨텍스트 간 연결

- 상태: 채택 (2026-11-07, 2026-11-08 보강)
- 관련: AGENTS.md 5장, FUNCTIONAL_SPEC 1장, docs/DEVIATIONS.md #4·#5·#9·#11·#16·#17

## 결정

1. **패키지 = 바운디드 컨텍스트**: `kr.securityadvisor.<ctx>/{controller,dto,service,domain,repository}`. 컨텍스트: request·review·document·configuration·knowledge·llm·auth·assignment·audit·policy·insight·notification·installation·common.
2. **domain 은 프레임워크 비의존**(JPA 애너테이션만). 상태 전이는 도메인 메서드(`submit()`·`returnToStage()`·`complete()`), setter 없음. 불변식 위반은 `DomainRuleViolationException`(422), 충돌은 `ConflictException`(409).
3. **컨텍스트 간 참조는 ID + 공개 서비스**. 다른 컨텍스트의 엔티티를 필드로 갖지 않는다. ArchUnit 이 controller→repository, controller→domain, domain→service/web/AI, 컨텍스트 순환을 금지한다.
4. **역방향 의존이 필요하면 두 가지만 쓴다**:
   - 포트 인터페이스: 하위 컨텍스트가 상위 사실을 필요로 할 때(`request.ReviewFactsProvider` ← `review.ReviewFactsAdapter`).
   - 같은 트랜잭션의 동기 `ApplicationEvent`: `AnswerChangedEvent`(request→review 재검토), `RequestConfirmedEvent`(review→policy 규칙 생성), `AnalysisQueued`(커밋 후 비동기). 이벤트 소싱·CQRS 는 도입하지 않는다.
5. **AI 호출이 필요한 유스케이스는 llm 컨텍스트에 둔다**(제안·질의·요약). 업무 코드는 `LlmGateway` 인터페이스만 보고 Spring AI 는 `llm.adapter` 밖에서 import 금지(ArchUnit).
6. **답변(answers)은 별도 리포지토리**(CASE/REQUEST 두 범위가 한 테이블을 쓰므로 컬렉션 매핑 대신 ID 로 묶음), 기한(Deadline)은 request 컨텍스트.
7. **감사 이력은 업무와 같은 트랜잭션**(`AuditRecorder`, REQUIRED). 알림은 반대로 별도 트랜잭션(REQUIRES_NEW)이며 실패해도 업무를 막지 않는다.
