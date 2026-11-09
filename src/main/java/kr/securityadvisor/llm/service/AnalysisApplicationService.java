package kr.securityadvisor.llm.service;

import java.util.ArrayList;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.domain.AnalysisJob;
import kr.securityadvisor.llm.domain.AnalysisStatus;
import kr.securityadvisor.llm.dto.AiProposalCommand;
import kr.securityadvisor.llm.dto.AnalysisApplicationCommand;
import kr.securityadvisor.llm.dto.AnalysisApplicationResult;
import kr.securityadvisor.llm.dto.AnalysisJobResponse;
import kr.securityadvisor.llm.repository.AnalysisJobRepository;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 문서 분석 결과 "적용"(F-02·F-03 연결): 완료된 요약을 사업 개요 항목에 AI 제안값으로 넣고, 미제출 신청마다 AI 항목 제안을 실행한다.
 * 모두 AI_INFERRED·미확정이며 사용자가 확인해야 한다. 모델이 없어도 요약 적용은 되고 제안 부분만 경고로 남긴다.
 *
 * <p>단계마다 호출하는 서비스가 각자 트랜잭션을 갖는다(이 메서드는 트랜잭션이 아님) — 한 단계가 실패해도 앞 단계 결과는 남고,
 * 실패는 경고로 보고한다. 실패한 단계의 예외를 같은 트랜잭션 안에서 삼키면 전체가 롤백 전용이 되기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class AnalysisApplicationService {

    static final String SUMMARY_FIELD = "project.summary";
    private static final int MAX_SUMMARY_VALUE = 4000;

    private final AnalysisJobRepository analysisJobRepository;
    private final RequestAccessPolicy accessPolicy;
    private final SecurityRequestRepository securityRequestRepository;
    private final AnswerService answerService;
    private final AiProposalService aiProposalService;
    private final AuditRecorder auditRecorder;

    public AnalysisApplicationResult apply(MemberPrincipal principal, Long jobId, AnalysisApplicationCommand command) {
        AnalysisJob job = analysisJobRepository
                .findById(jobId)
                .orElseThrow(() -> new NotFoundException("분석 작업을 찾을 수 없습니다: " + jobId));
        BusinessCase businessCase = accessPolicy.ownedCase(principal, job.getBusinessCaseId());
        businessCase.requireRevision(command.expectedRevision());
        if (job.getStatus() != AnalysisStatus.NEEDS_REVIEW || job.getSummary() == null) {
            throw new DomainRuleViolationException(
                    "완료된(확인 필요) 분석 작업만 적용할 수 있습니다 (현재 " + job.getStatus().label() + ")");
        }
        List<SecurityRequest> unsubmitted =
                securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCase.getId()).stream()
                        .filter(SecurityRequest::isUnsubmitted)
                        .toList();
        if (unsubmitted.isEmpty()) {
            throw new DomainRuleViolationException("진행 중(미제출)인 신청이 없어 분석 결과를 적용할 수 없습니다");
        }

        List<String> warnings = new ArrayList<>();
        AnswerResponse summaryProposal = null;
        SecurityRequest anchor = unsubmitted.get(0);
        // 사용자가 이미 확인한(적은) 개요는 덮어쓰지 않는다 — 미리 확인하고 호출한다(불변식: 확인된 값은 AI 제안으로 덮어쓰지 않음)
        boolean summaryConfirmed = answerService.allAnswers(anchor).stream()
                .anyMatch(a -> SUMMARY_FIELD.equals(a.fieldKey())
                        && a.confirmed()
                        && a.value() != null
                        && !a.value().isBlank());
        if (summaryConfirmed) {
            warnings.add("사업 개요(" + SUMMARY_FIELD + ")는 사용자가 확인한 값이 있어 AI 제안으로 덮어쓰지 않았습니다");
        } else {
            String value = job.getSummary().strip();
            summaryProposal = answerService.proposeByAi(
                    principal,
                    anchor,
                    SUMMARY_FIELD,
                    value.length() > MAX_SUMMARY_VALUE ? value.substring(0, MAX_SUMMARY_VALUE) : value,
                    "분석 작업 " + jobId + " (파일 " + job.getUploadedFileId() + ")",
                    job.getModelReference() == null ? "analysis" : job.getModelReference());
        }

        List<AnalysisApplicationResult.RequestProposals> proposals = new ArrayList<>();
        for (SecurityRequest request : unsubmitted) {
            try {
                proposals.add(new AnalysisApplicationResult.RequestProposals(
                        request.getId(),
                        aiProposalService.propose(principal, request.getId(), new AiProposalCommand(null))));
            } catch (ServiceUnavailableException e) {
                warnings.add("신청 " + request.getId() + " AI 항목 제안 실패(모델 사용 불가): " + e.getMessage());
            }
        }
        auditRecorder.record(
                principal,
                AuditTargetType.BUSINESS_CASE,
                String.valueOf(businessCase.getId()),
                businessCase.getRevision(),
                AuditAction.ANALYSIS_APPLIED,
                null,
                "문서 AI 요약 적용 (작업 " + jobId + ", 제안 실행 " + proposals.size() + "건, 경고 " + warnings.size()
                        + "건) — 전부 미확정");
        return new AnalysisApplicationResult(AnalysisJobResponse.from(job), summaryProposal, proposals, warnings);
    }
}
