package kr.securityadvisor.llm.service;

import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.common.security.MemberPrincipal;

/**
 * AI 호출의 감사 맥락(F-14 "AI 호출: 구성·용도·대상·성공/실패·소요 시간"). 프롬프트·응답 원문은 담지 않는다.
 *
 * @param actor 호출을 일으킨 사용자(비동기 작업이면 요청자, 없으면 null)
 * @param targetType 대상 유형(신청·사업 건 등)
 * @param targetId 대상 ID
 */
public record LlmCallContext(MemberPrincipal actor, AuditTargetType targetType, String targetId) {

    public static LlmCallContext of(MemberPrincipal actor, AuditTargetType targetType, Long targetId) {
        return new LlmCallContext(actor, targetType, String.valueOf(targetId));
    }
}
