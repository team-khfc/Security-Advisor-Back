package kr.securityadvisor.request.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.common.calendar.BusinessCalendar;
import kr.securityadvisor.common.schema.DeadlinePolicy;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.service.Notifier;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.Deadline;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.DeadlineReminderRunCommand;
import kr.securityadvisor.request.dto.DeadlineReminderRunResult;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.DeadlineRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기한 알림(F-13·F-17): 활성 기한을 훑어 <b>게시본의 임박 영업일 범위</b>과 <b>기한 경과</b> 알림을 만든다. 기한 상태는 저장하지 않고 계산하므로(불변식 22)
 * 이 서비스도 상태를 바꾸지 않고 알림만 남긴다. 같은 기한·같은 종류 알림은 event_key 로 한 번만 간다(수신자별 유일).
 *
 * <p>수신자: 협의 기한 → 그 분야의 활성 담당자, 보완 응답 기한 → 신청자, 최종 확정 기한 → 두 분야의 활성 담당자.
 * 종료 상태(종결·철회·확정)의 신청은 건너뛴다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeadlineReminderService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final DeadlineRepository deadlineRepository;
    private final SecurityRequestRepository securityRequestRepository;
    private final BusinessCaseRepository businessCaseRepository;
    private final PublicationService publicationService;
    private final AssignmentService assignmentService;
    private final Notifier notifier;
    private final Clock clock;
    private final List<OperationalWork> operationalWork;

    /** 스케줄러가 부르는 기본 실행(오늘 기준). */
    public DeadlineReminderRunResult runToday() {
        return run(LocalDate.ofInstant(clock.instant(), KOREA), null);
    }

    /** 기관 관리자의 수동 실행. 기준일을 주면 그 날짜 기준으로 계산한다(시연·검증용). */
    public DeadlineReminderRunResult runAs(MemberPrincipal admin, DeadlineReminderRunCommand command) {
        admin.requireAnyRole(MemberRole.ADMIN);
        LocalDate asOf = command == null || command.asOf() == null
                ? LocalDate.ofInstant(clock.instant(), KOREA)
                : command.asOf();
        return run(asOf, command == null ? null : command.securityRequestId());
    }

    private DeadlineReminderRunResult run(LocalDate asOf, Long onlyRequestId) {
        Map<Long, DeadlinePolicy> policies = new HashMap<>();
        int scanned = 0;
        int imminent = 0;
        int overdue = 0;
        for (Deadline deadline : deadlineRepository.findByActiveTrue()) {
            if (onlyRequestId != null && !onlyRequestId.equals(deadline.getSecurityRequestId())) continue;
            SecurityRequest request = securityRequestRepository
                    .findById(deadline.getSecurityRequestId())
                    .orElse(null);
            if (request == null || request.isTerminal()) {
                continue;
            }
            BusinessCase businessCase =
                    businessCaseRepository.findById(request.getBusinessCaseId()).orElse(null);
            if (businessCase == null) {
                continue;
            }
            scanned++;
            DeadlinePolicy policy = policies.computeIfAbsent(
                    businessCase.getPublicationId(),
                    id -> publicationService.get(id).fieldSchema().deadlinePolicy());
            BusinessCalendar calendar = new BusinessCalendar(policy.holidays());
            LocalDate due = deadline.getDueDate();
            if (asOf.isAfter(due)) {
                overdue++;
                send(
                        deadline,
                        request,
                        businessCase,
                        NotificationKind.DEADLINE_OVERDUE,
                        "overdue",
                        "기한 지남 (" + due + ")");
            } else if (calendar.businessDaysBetween(asOf, due) <= policy.imminentBusinessDays()) {
                imminent++;
                send(
                        deadline,
                        request,
                        businessCase,
                        NotificationKind.DEADLINE_IMMINENT,
                        "imminent",
                        "기한 임박 (" + policy.imminentBusinessDays() + "영업일 이내, " + due + ")");
            }
        }
        operationalWork.forEach(work -> work.remind(asOf, onlyRequestId));
        log.info("기한 알림 실행 기준일 {}: 검사 {}건, 임박 {}건, 경과 {}건", asOf, scanned, imminent, overdue);
        return new DeadlineReminderRunResult(asOf, scanned, imminent, overdue);
    }

    private void send(
            Deadline deadline,
            SecurityRequest request,
            BusinessCase businessCase,
            NotificationKind kind,
            String keySuffix,
            String message) {
        notifier.notify(
                recipientsOf(deadline, request, businessCase),
                "deadline:" + deadline.getId() + ":" + keySuffix,
                kind,
                "신청 #" + request.getId() + " " + label(deadline) + " " + message,
                "/api/security-requests/" + request.getId() + "/deadlines");
    }

    private List<String> recipientsOf(Deadline deadline, SecurityRequest request, BusinessCase businessCase) {
        List<String> recipients = new ArrayList<>();
        switch (deadline.getKind()) {
            case REVIEW ->
                assignmentService
                        .eligibleAssignee(request.getId(), deadline.getField())
                        .ifPresent(recipients::add);
            case SUPPLEMENT_RESPONSE -> recipients.add(businessCase.getApplicantUsername());
            case FINAL_CONFIRMATION -> {
                for (ReviewField field : ReviewField.values()) {
                    assignmentService.eligibleAssignee(request.getId(), field).ifPresent(recipients::add);
                }
            }
        }
        return recipients;
    }

    private static String label(Deadline deadline) {
        return switch (deadline.getKind()) {
            case REVIEW -> (deadline.getField() == ReviewField.INFRA ? "ICT인프라" : "정보보안") + " 협의 기한";
            case SUPPLEMENT_RESPONSE -> "보완 응답 기한";
            case FINAL_CONFIRMATION -> "최종 확정 기한";
        };
    }
}
