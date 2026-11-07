package kr.securityadvisor.request.service;

import java.util.List;
import kr.securityadvisor.common.security.MemberPrincipal;

/**
 * 답변이 사용자에 의해 바뀌었음을 알리는 도메인 이벤트(같은 트랜잭션에서 동기 전달).
 * review 컨텍스트가 받아 완료된 협의를 재검토로 돌린다(불변식 7) — request 는 review 를 모른다.
 *
 * @param actor 답변을 바꾼 사용자
 * @param affectedRequestIds 영향받는 미제출 신청(공통 답변이면 사업 건의 모든 미제출 신청)
 * @param fieldKey 바뀐 항목
 */
public record AnswerChangedEvent(MemberPrincipal actor, List<Long> affectedRequestIds, String fieldKey) {}
