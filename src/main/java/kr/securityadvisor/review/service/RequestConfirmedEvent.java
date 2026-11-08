package kr.securityadvisor.review.service;

import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.security.MemberPrincipal;

/**
 * 신청 확정 도메인 이벤트(같은 트랜잭션, 동기). policy 컨텍스트가 받아 방화벽 규칙을 만든다(불변식 26). review 는 policy 를 모른다.
 */
public record RequestConfirmedEvent(
        MemberPrincipal actor, Long securityRequestId, Long submissionId, DocTypeCode docType) {}
