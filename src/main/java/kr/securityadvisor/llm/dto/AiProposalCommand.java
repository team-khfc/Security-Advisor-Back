package kr.securityadvisor.llm.dto;

import java.util.List;

/**
 * AI 제안 요청(POST /api/security-requests/{id}/ai-proposals).
 *
 * @param fieldKeys 비우면 제안 가능한 모든 항목(값이 없거나 미확정 AI 값인 신청자 항목)
 */
public record AiProposalCommand(List<String> fieldKeys) {}
