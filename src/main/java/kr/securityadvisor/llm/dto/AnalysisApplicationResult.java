package kr.securityadvisor.llm.dto;

import java.util.List;
import kr.securityadvisor.request.dto.AnswerResponse;

/**
 * 분석 결과 적용 결과. 전부 AI_INFERRED·미확정 값이며 사용자가 확인해야 확정된다(불변 원칙 1).
 *
 * @param job 적용한 분석 작업
 * @param summaryProposal 사업 개요(project.summary)에 넣은 AI 제안 값(항목이 없거나 담당자 항목이면 null)
 * @param proposals 미제출 신청마다 실행한 AI 항목 제안 결과
 * @param warnings 실행하지 못한 부분(예: 모델 사용 불가)
 */
public record AnalysisApplicationResult(
        AnalysisJobResponse job,
        AnswerResponse summaryProposal,
        List<RequestProposals> proposals,
        List<String> warnings) {

    public record RequestProposals(Long securityRequestId, AiProposalResult result) {}
}
