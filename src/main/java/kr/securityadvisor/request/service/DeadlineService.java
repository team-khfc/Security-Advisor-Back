package kr.securityadvisor.request.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.calendar.BusinessCalendar;
import kr.securityadvisor.common.schema.DeadlinePolicy;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.dto.PublicationResponse;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.request.domain.Deadline;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.DeadlineResponse;
import kr.securityadvisor.request.repository.DeadlineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 처리 기한 유스케이스(F-13). 기한 정책은 사업 건이 고정한 게시본에서 읽고, 영업일 계산은 {@link BusinessCalendar} 가 한다.
 * 기한 경과는 상태를 바꾸지 않으며 표시(조회 시 계산)와 알림만 한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DeadlineService {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final DeadlineRepository deadlineRepository;
    private final PublicationService publicationService;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    /** 2단계 진입: 분야별 협의 기한을 만든다(이미 활성 기한이 있는 분야는 건너뜀). */
    public List<DeadlineResponse> startReviewDeadlines(
            MemberPrincipal actor, SecurityRequest request, Long publicationId, Set<ReviewField> fields) {
        PublicationContext context = contextOf(publicationId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KOREA);
        List<Deadline> existing =
                deadlineRepository.findBySecurityRequestIdAndKindAndActiveTrue(request.getId(), DeadlineKind.REVIEW);
        return fields.stream()
                .filter(field -> existing.stream().noneMatch(deadline -> deadline.getField() == field))
                .map(field -> start(
                        actor,
                        request,
                        field,
                        DeadlineKind.REVIEW,
                        now,
                        today,
                        context.policy().reviewBusinessDays(),
                        context))
                .toList();
    }

    /** 보완 요청·차수 반환: 기존 보완 응답 기한을 종료하고 새로 만든다(불변식 23). */
    public DeadlineResponse restartSupplementDeadline(
            MemberPrincipal actor, SecurityRequest request, Long publicationId) {
        PublicationContext context = contextOf(publicationId);
        Instant now = clock.instant();
        endActive(actor, request.getId(), DeadlineKind.SUPPLEMENT_RESPONSE, now, "재산정");
        return start(
                actor,
                request,
                null,
                DeadlineKind.SUPPLEMENT_RESPONSE,
                now,
                LocalDate.ofInstant(now, KOREA),
                context.policy().supplementBusinessDays(),
                context);
    }

    /**
     * 보완 요청으로 신청자 답변을 기다리는 동안 그 분야의 협의 기한을 정지한다(F-13 P3). 정지 = 종료 + 남은 영업일 보관.
     * 활성 협의 기한이 없으면 아무것도 하지 않는다.
     */
    public void pauseReviewDeadline(
            MemberPrincipal actor, SecurityRequest request, ReviewField field, Long publicationId) {
        PublicationContext context = contextOf(publicationId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KOREA);
        for (Deadline deadline :
                deadlineRepository.findBySecurityRequestIdAndKindAndActiveTrue(request.getId(), DeadlineKind.REVIEW)) {
            if (deadline.getField() != field) {
                continue;
            }
            int remaining = deadline.pause(now, today, context.calendar());
            auditRecorder.record(
                    actor,
                    AuditTargetType.SECURITY_REQUEST,
                    String.valueOf(request.getId()),
                    null,
                    AuditAction.DEADLINE_ENDED,
                    "보완 대기 정지",
                    "REVIEW/" + field + " 기한 정지 — 남은 영업일 " + remaining + " (기한 " + deadline.getId() + ")");
        }
        deadlineRepository.flush();
    }

    /**
     * 보완 답변으로 담당자 검토가 재개되면 정지해 둔 협의 기한을 남은 영업일만큼으로 다시 만든다. 정지된 기한이 없으면 아무것도 하지 않는다.
     */
    public Optional<DeadlineResponse> resumeReviewDeadline(
            MemberPrincipal actor, SecurityRequest request, ReviewField field, Long publicationId) {
        List<Deadline> paused =
                deadlineRepository.findBySecurityRequestIdAndKindAndFieldAndPausedTrueOrderByEndedAtDescIdDesc(
                        request.getId(), DeadlineKind.REVIEW, field);
        if (paused.isEmpty()) {
            return Optional.empty();
        }
        boolean alreadyActive =
                deadlineRepository
                        .findBySecurityRequestIdAndKindAndActiveTrue(request.getId(), DeadlineKind.REVIEW)
                        .stream()
                        .anyMatch(d -> d.getField() == field);
        int remaining = paused.get(0).consumePause();
        paused.stream().skip(1).forEach(Deadline::consumePause); // 오래된 정지 기록은 소비만 하고 버린다
        if (alreadyActive) {
            return Optional.empty();
        }
        PublicationContext context = contextOf(publicationId);
        Instant now = clock.instant();
        deadlineRepository.flush();
        return Optional.of(start(
                actor, request, field, DeadlineKind.REVIEW, now, LocalDate.ofInstant(now, KOREA), remaining, context));
    }

    /** 제출: 최종 확정 기한을 만든다. */
    public DeadlineResponse startFinalConfirmationDeadline(
            MemberPrincipal actor, SecurityRequest request, Long publicationId) {
        PublicationContext context = contextOf(publicationId);
        Instant now = clock.instant();
        endActive(actor, request.getId(), DeadlineKind.FINAL_CONFIRMATION, now, "재산정");
        return start(
                actor,
                request,
                null,
                DeadlineKind.FINAL_CONFIRMATION,
                now,
                LocalDate.ofInstant(now, KOREA),
                context.policy().finalConfirmationBusinessDays(),
                context);
    }

    /** 단계가 끝날 때(협의 완료·종결·철회 등) 해당 종류의 활성 기한을 모두 종료한다. 기록은 보존된다. */
    public void endActive(
            MemberPrincipal actor, Long securityRequestId, DeadlineKind kind, Instant now, String reason) {
        for (Deadline deadline :
                deadlineRepository.findBySecurityRequestIdAndKindAndActiveTrue(securityRequestId, kind)) {
            deadline.end(now);
            auditRecorder.record(
                    actor,
                    AuditTargetType.SECURITY_REQUEST,
                    String.valueOf(securityRequestId),
                    null,
                    AuditAction.DEADLINE_ENDED,
                    reason,
                    kind + (deadline.getField() == null ? "" : "/" + deadline.getField()) + " 기한 종료 (기한 "
                            + deadline.getId() + ")");
        }
        // 부분 유일 인덱스(활성 기한 1건) 때문에 새 기한 INSERT 전에 종료 UPDATE 를 먼저 내보낸다.
        deadlineRepository.flush();
    }

    /** 신청의 기한 목록(활성·종료 모두). 상태는 오늘 기준 계산. */
    @Transactional(readOnly = true)
    public List<DeadlineResponse> listForRequest(Long securityRequestId, Long publicationId) {
        PublicationContext context = contextOf(publicationId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), KOREA);
        return deadlineRepository.findBySecurityRequestIdOrderByStartedAtAscIdAsc(securityRequestId).stream()
                .map(deadline -> DeadlineResponse.from(
                        deadline,
                        deadline.statusOn(
                                today, context.calendar(), context.policy().imminentBusinessDays())))
                .toList();
    }

    private DeadlineResponse start(
            MemberPrincipal actor,
            SecurityRequest request,
            ReviewField field,
            DeadlineKind kind,
            Instant now,
            LocalDate startDate,
            int businessDays,
            PublicationContext context) {
        Deadline deadline = deadlineRepository.save(Deadline.start(
                request.getId(), field, kind, now, startDate, businessDays, context.calendar(), context.version()));
        auditRecorder.record(
                actor,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(request.getId()),
                null,
                AuditAction.DEADLINE_STARTED,
                null,
                kind + (field == null ? "" : "/" + field) + " 기한일 " + deadline.getDueDate() + " (기한 " + deadline.getId()
                        + ")");
        return DeadlineResponse.from(
                deadline,
                deadline.statusOn(
                        startDate, context.calendar(), context.policy().imminentBusinessDays()));
    }

    private PublicationContext contextOf(Long publicationId) {
        PublicationResponse publication = publicationService.get(publicationId);
        FieldSchema schema = publication.fieldSchema();
        DeadlinePolicy policy = schema.deadlinePolicy();
        return new PublicationContext(publication.version(), policy, new BusinessCalendar(policy.holidays()));
    }

    private record PublicationContext(int version, DeadlinePolicy policy, BusinessCalendar calendar) {}
}
