package kr.securityadvisor.review.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.repository.SubmissionRepository;
import kr.securityadvisor.review.domain.ReviewStatus;
import kr.securityadvisor.review.domain.SupplementStatus;
import kr.securityadvisor.review.dto.TodoItem;
import kr.securityadvisor.review.dto.TodoSummary;
import kr.securityadvisor.review.repository.FinalConfirmationRepository;
import kr.securityadvisor.review.repository.ReviewRepository;
import kr.securityadvisor.review.repository.SupplementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 처리할 일 목록과 배지를 같은 업무 판정에서 계산한다. 읽음 처리로 업무가 완료되지 않는다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TodoService {
    private final AssignmentService assignmentService;
    private final ReviewRepository reviewRepository;
    private final SupplementRepository supplementRepository;
    private final SubmissionRepository submissionRepository;
    private final FinalConfirmationRepository finalConfirmationRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final BusinessCaseRepository businessCaseRepository;
    private final RequiredReviewFields requiredReviewFields;

    public TodoSummary summarize(MemberPrincipal principal) {
        List<TodoItem> items = items(principal);
        return TodoSummary.of(
                principal.role(),
                count(items, "REVIEW"),
                count(items, "SUPPLEMENT_ANSWERED"),
                count(items, "FINAL_CONFIRMATION"),
                count(items, "SUPPLEMENT_OPEN"),
                count(items, "FINAL_REVIEW"),
                count(items, "REASSIGNMENT"));
    }

    public PageResponse<TodoItem> list(MemberPrincipal principal, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("페이지는 0 이상, 크기는 1~100이어야 합니다");
        List<TodoItem> items = items(principal);
        return new PageResponse<>(
                items.stream().skip((long) page * size).limit(size).toList(), page, size, items.size());
    }

    private List<TodoItem> items(MemberPrincipal principal) {
        List<TodoItem> items = new ArrayList<>();
        if (principal.role() == MemberRole.ADMIN) {
            for (SecurityRequest r : securityRequestRepository.findAll()) {
                if (r.getStage() >= 2
                        && !r.isTerminal()
                        && requiredReviewFields.of(r).stream()
                                .anyMatch(field -> assignmentService.reassignmentReason(r.getId(), field) != null))
                    items.add(item("REASSIGNMENT", r.getId(), r.getId(), "담당자 재배정"));
            }
        } else if (principal.role() == MemberRole.APPLICANT) {
            for (var c : businessCaseRepository.findByApplicantUsernameOrderByIdAsc(principal.username())) {
                for (SecurityRequest r : securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(c.getId())) {
                    if (r.isTerminal()) continue;
                    if (r.getStatus() == RequestStatus.FINAL_REVIEW)
                        items.add(item("FINAL_REVIEW", r.getId(), r.getId(), "최종 초안 확인·제출"));
                    supplementRepository.findBySecurityRequestIdOrderByRequestedAtAscIdAsc(r.getId()).stream()
                            .filter(s -> s.getStatus() == SupplementStatus.OPEN)
                            .forEach(s -> items.add(item("SUPPLEMENT_OPEN", s.getId(), r.getId(), "보완 답변 작성")));
                }
            }
        } else {
            for (var a : assignmentService.listMine(principal)) {
                SecurityRequest r = securityRequestRepository
                        .findById(a.securityRequestId())
                        .orElse(null);
                if (r == null || r.isTerminal()) continue;
                if (r.getStage() == 2
                        && reviewRepository
                                .findBySecurityRequestIdAndField(r.getId(), a.field())
                                .map(review -> review.getStatus() != ReviewStatus.COMPLETED)
                                .orElse(false)) items.add(item("REVIEW", r.getId(), r.getId(), "담당자 협의"));
                if (r.getStage() == 2 || r.getStage() == 3) {
                    supplementRepository.findBySecurityRequestIdOrderByRequestedAtAscIdAsc(r.getId()).stream()
                            .filter(s -> s.getField() == a.field() && s.getStatus() == SupplementStatus.ANSWERED)
                            .forEach(s -> items.add(item("SUPPLEMENT_ANSWERED", s.getId(), r.getId(), "보완 답변 검토·해소")));
                }
                if (r.getStatus() == RequestStatus.SUBMITTED && awaitsFinal(r, a.field()))
                    items.add(item("FINAL_CONFIRMATION", r.getId(), r.getId(), "제출본 최종 확인·서류 확정"));
            }
        }
        return items.stream()
                .sorted(Comparator.comparing(TodoItem::securityRequestId)
                        .reversed()
                        .thenComparing(TodoItem::key))
                .toList();
    }

    private boolean awaitsFinal(SecurityRequest request, ReviewField field) {
        var latest = submissionRepository
                .findFirstBySecurityRequestIdOrderBySequenceNoDesc(request.getId())
                .orElse(null);
        if (latest == null) return false;
        boolean confirmed = finalConfirmationRepository.existsBySubmissionIdAndField(latest.getId(), field);
        if (confirmed)
            return field
                            == (requiredReviewFields.of(request).contains(ReviewField.SECURITY)
                                    ? ReviewField.SECURITY
                                    : ReviewField.INFRA)
                    && requiredReviewFields.of(request).stream()
                            .allMatch(f -> finalConfirmationRepository.existsBySubmissionIdAndField(latest.getId(), f));
        return field == ReviewField.INFRA
                || !requiredReviewFields.infraPrecedes(requiredReviewFields.of(request))
                || finalConfirmationRepository.existsBySubmissionIdAndField(latest.getId(), ReviewField.INFRA);
    }

    private static TodoItem item(String kind, Long id, Long requestId, String action) {
        return new TodoItem(
                kind + ":" + id, kind, requestId, "신청 #" + requestId + " · " + action, "/requests/" + requestId);
    }

    private static long count(List<TodoItem> items, String kind) {
        return items.stream().filter(i -> i.kind().equals(kind)).count();
    }
}
