package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.Size;

/**
 * AI 서류 유형 제안 요청(F-01). 사업 개요(직접 입력)와, 있으면 기존 사업 건의 문서 AI 요약을 근거로 쓴다.
 *
 * @param summary 사업·서비스 개요(직접 입력, 선택 — businessCaseId 가 없으면 필수)
 * @param businessCaseId 기존 사업 건(선택). 그 사업 건의 개요·완료된 문서 요약을 함께 넣는다
 */
public record DocTypeSuggestionCommand(@Size(max = 4000) String summary, Long businessCaseId) {}
