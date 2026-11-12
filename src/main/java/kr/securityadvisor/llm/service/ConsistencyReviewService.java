package kr.securityadvisor.llm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.*;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.audit.domain.*;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.*;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.llm.domain.ConsistencyReview;
import kr.securityadvisor.llm.dto.*;
import kr.securityadvisor.llm.repository.ConsistencyReviewRepository;
import kr.securityadvisor.request.dto.ConsistencyCheckResponse;
import kr.securityadvisor.request.dto.ConsistencyReviewResponse;
import kr.securityadvisor.request.dto.ConsistencyReviewResponse.Decision;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.review.dto.SupplementRequestCommand;
import kr.securityadvisor.review.service.ReviewService;
import kr.securityadvisor.review.service.SupplementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 현재 배정 담당자의 판단을 기록한다. AI 후보나 읽음 처리는 업무의 확정이 아니다. */
@Service
@RequiredArgsConstructor
@Transactional
public class ConsistencyReviewService implements kr.securityadvisor.request.service.ConsistencyFactsProvider {
    public java.util.List<ConsistencyReviewResponse> snapshot(MemberPrincipal actor, Long requestId) {
        var records = new ArrayList<ConsistencyReviewResponse>();
        for (int page = 0; ; page++) {
            var batch = list(actor, requestId, page);
            records.addAll(batch);
            if (batch.size() < 10) return List.copyOf(records);
        }
    }

    private final ConsistencyReviewRepository repository;
    private final RequestAccessPolicy access;
    private final ConsistencyCheckService checks;
    private final AssignmentService assignments;
    private final ReviewService reviews;
    private final SupplementService supplements;
    private final AuditRecorder audit;
    private final ObjectMapper json;
    private final Clock clock;
    private final EntityManager entities;
    private final kr.securityadvisor.configuration.service.PublicationService publications;

    public void save(MemberPrincipal actor, Long requestId, ConsistencyCheckResponse result) {
        access.viewableRequest(actor, requestId);
        repository.save(ConsistencyReview.create(requestId, json.writeValueAsString(result)));
    }

    @Transactional(readOnly = true)
    public List<ConsistencyReviewResponse> list(MemberPrincipal actor, Long requestId, int page) {
        access.viewableRequest(actor, requestId);
        if (page < 0) throw new BadRequestException("페이지는 0 이상이어야 합니다.");
        return repository.findBySecurityRequestIdOrderByIdDesc(requestId, PageRequest.of(page, 10)).stream()
                .map(r -> response(actor, r))
                .toList();
    }

    public ConsistencyReviewResponse decide(
            MemberPrincipal actor, Long requestId, Long id, ConsistencyDecisionCommand command) {
        var request = access.viewableRequest(actor, requestId);
        actor.requireAnyRole(MemberRole.INFRA, MemberRole.SECURITY);
        if (!assignments.isActiveAssignee(
                requestId, ReviewField.valueOf(actor.role().name()), actor.username()))
            throw new ForbiddenException("현재 배정된 담당자만 처리할 수 있습니다.");
        entities.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        var business = access.viewableCase(actor, request.getBusinessCaseId());
        entities.refresh(business, LockModeType.PESSIMISTIC_WRITE);
        if (!request.isUnsubmitted() || request.getStage() < 2)
            throw new DomainRuleViolationException("협의·최종 검토 단계에서만 처리할 수 있습니다.");
        var row = repository
                .findById(id)
                .filter(r -> r.getSecurityRequestId().equals(requestId))
                .orElseThrow(() -> new NotFoundException("검사 기록이 없습니다."));
        entities.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        var current = response(actor, row);
        if (current.stale() || !current.result().fingerprint().equals(command.expectedFingerprint()))
            throw new ConflictException("검사 이후 자료가 바뀌었습니다. 다시 검사해 주세요.");
        if (!current.allowedFindings().contains(command.findingIndex()))
            throw new ForbiddenException("현재 담당 분야에서 처리할 수 없는 후보입니다. 최신 검사와 배정을 확인해 주세요.");
        if (command.findingIndex() < 0
                || command.findingIndex() >= current.result().findings().size()
                || !Set.of("RESOLVED", "DISMISSED", "SUPPLEMENT_REQUESTED", "OPEN")
                        .contains(command.status())
                || command.reason() == null
                || command.reason().isBlank()
                || command.reason().length() > 2000) throw new BadRequestException("처리 대상·상태·사유를 확인해 주세요.");
        if (row.getRevision() != command.expectedRevision()) throw new ConflictException("처리 기록이 변경됐습니다.");
        Long supplementId = null;
        if ("SUPPLEMENT_REQUESTED".equals(command.status())) {
            var review = reviews.listForRequest(actor, requestId).stream()
                    .filter(r -> r.field().name().equals(actor.role().name()))
                    .findFirst()
                    .orElseThrow(() -> new DomainRuleViolationException("담당 분야 협의가 없습니다."));
            String key = current.result().findings().get(command.findingIndex()).fieldKey();
            supplementId = supplements
                    .request(
                            actor,
                            review.reviewId(),
                            new SupplementRequestCommand(List.of(key), command.reason(), review.revision()))
                    .supplementId();
        }
        var history = new ArrayList<>(current.decisions());
        history.add(new Decision(
                command.findingIndex(),
                command.status(),
                command.reason(),
                actor.displayName(),
                clock.instant(),
                supplementId));
        row.decide(command.expectedRevision(), json.writeValueAsString(history));
        repository.flush();
        audit.record(
                actor,
                AuditTargetType.SECURITY_REQUEST,
                requestId.toString(),
                row.getRevision(),
                AuditAction.CONSISTENCY_DECIDED,
                command.reason(),
                "모순 후보 처리 " + id + ":" + command.findingIndex() + " " + command.status());
        return response(actor, row);
    }

    private ConsistencyReviewResponse response(MemberPrincipal actor, ConsistencyReview row) {
        var result = json.readValue(row.getResult(), ConsistencyCheckResponse.class);
        var request = access.viewableRequest(actor, row.getSecurityRequestId());
        boolean stale = !result.fingerprint()
                .equals(checks.fingerprint(
                        actor,
                        request.getId(),
                        result.sources().stream()
                                .map(ConsistencyCheckResponse.Source::fileId)
                                .toList()));
        boolean allowed = !stale
                && request.isUnsubmitted()
                && request.getStage() >= 2
                && (actor.role() == MemberRole.INFRA || actor.role() == MemberRole.SECURITY)
                && assignments.isActiveAssignee(
                        request.getId(), ReviewField.valueOf(actor.role().name()), actor.username());
        var business = access.viewableCase(actor, request.getBusinessCaseId());
        var schema = publications.getFieldSchema(business.getPublicationId());
        var allowedIndexes = new ArrayList<Integer>();
        if (allowed)
            for (int i = 0; i < result.findings().size(); i++) {
                String key = result.findings().get(i).fieldKey();
                boolean fieldAllowed = key.startsWith("context.")
                        || schema.find(key)
                                .map(f -> f.askRole() == MemberRole.APPLICANT || f.askRole() == actor.role())
                                .orElse(false);
                if (fieldAllowed) allowedIndexes.add(i);
            }
        return new ConsistencyReviewResponse(
                row.getId(),
                row.getRevision(),
                stale,
                allowed,
                allowedIndexes,
                result,
                List.of(json.readValue(row.getDecisions(), Decision[].class)));
    }
}
