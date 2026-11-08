package kr.securityadvisor.llm.dto;

import java.util.List;
import kr.securityadvisor.request.dto.AnswerResponse;

/**
 * AI 제안 결과. proposed 는 AI_INFERRED·미확정으로 저장된 답변(사용자 확인 전까지 확정 아님), skipped 는 제안하지 않은 항목과 이유.
 *
 * @param modelReference 구성 이름/모델명(키 없음)
 */
public record AiProposalResult(String modelReference, List<AnswerResponse> proposed, List<Skipped> skipped) {

    public record Skipped(String fieldKey, String reason) {}
}
