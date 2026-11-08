package kr.securityadvisor.document.dto;

/**
 * 서류 생성(POST /api/security-requests/{id}/generated-documents).
 *
 * @param submissionId 지정하면 그 제출본 스냅샷으로(제출본/확정본), 비우면 현재 답변으로 초안
 * @param aiSupplement true 면 AI 가 값을 바탕으로 보완 서술을 덧붙인다(미확정 참고 문장, 값에 반영되지 않음)
 */
public record GenerateDocumentCommand(Long submissionId, Boolean aiSupplement) {}
