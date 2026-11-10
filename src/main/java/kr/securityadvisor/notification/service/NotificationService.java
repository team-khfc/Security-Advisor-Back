package kr.securityadvisor.notification.service;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.ReviewerEligibilityChanged;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.notification.domain.Notification;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.dto.NotificationResponse;
import kr.securityadvisor.notification.repository.NotificationAudience;
import kr.securityadvisor.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** 화면 안 알림(F-17). 발송은 별도 트랜잭션에서 하고 실패는 로그만 남긴다. 조회·읽음은 수신자 본인만. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements Notifier {

    private final NotificationRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final ExternalNotificationDispatcher dispatcher;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final NotificationAudience audience;

    /** 업무 커밋 전에는 알림을 저장하거나 외부로 전달하지 않는다. */
    public record PendingNotification(
            List<String> recipients, String eventKey, NotificationKind kind, String title, String link) {}

    @Override
    public void notify(
            Collection<String> recipients, String eventKey, NotificationKind kind, String title, String link) {
        if (recipients == null) {
            return;
        }
        events.publishEvent(new PendingNotification(
                recipients.stream()
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList(),
                eventKey,
                kind,
                title,
                link));
    }

    /** 롤백된 업무는 전달되지 않는다. 알림 실패는 이미 성공한 업무에 영향을 주지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(PendingNotification pending) {
        String eventKey = pending.eventKey();
        for (String recipient : pending.recipients()) {
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            try {
                // 업무 트랜잭션과 분리(REQUIRES_NEW): 알림 저장 실패가 업무 변경을 롤백시키지 않는다(불변식 30)
                TransactionTemplate separate = new TransactionTemplate(transactionTemplate.getTransactionManager());
                separate.setPropagationBehavior(
                        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                Notification created = separate.execute(status -> {
                    Long requestId = audience.requestId(pending.link());
                    var role = audience.eligibleRole(recipient, requestId, pending.kind());
                    if (role.isEmpty()) return null;
                    if (repository.existsByRecipientUsernameAndEventKey(recipient, eventKey)) {
                        return null;
                    }
                    Notification notification = Notification.of(
                            recipient,
                            eventKey,
                            pending.kind(),
                            pending.title().replace("INFRA", "ICT인프라").replace("SECURITY", "정보보안"),
                            pending.link());
                    notification.bindAudience(requestId, role.get());
                    return repository.save(notification);
                });
                if (created != null) {
                    dispatcher.dispatch(created); // 메일·메신저는 비동기, 실패해도 화면 알림은 남는다
                }
            } catch (RuntimeException e) {
                log.warn(
                        "알림 생성 실패 recipient={} eventKey={} ({})",
                        recipient,
                        eventKey,
                        e.getClass().getSimpleName());
            }
        }
    }

    /** 역할 변경·계정 또는 분야 등록 비활성화가 커밋된 뒤 인계 필요 알림을 만든다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void eligibilityChanged(ReviewerEligibilityChanged event) {
        try {
            TransactionTemplate separate = new TransactionTemplate(transactionTemplate.getTransactionManager());
            separate.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            separate.executeWithoutResult(status -> {
                for (Long id : audience.handoffRequests(event.username())) {
                    notify(
                            audience.admins(),
                            "handoff:" + event.eventKey() + ":" + id,
                            NotificationKind.REASSIGNMENT_REQUIRED,
                            "신청 #" + id + " 담당자 변경으로 재배정이 필요합니다",
                            "/requests/" + id);
                }
            });
        } catch (RuntimeException e) {
            log.warn("업무 인계 알림 생성 실패 ({})", e.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(MemberPrincipal principal, boolean unreadOnly, int page, int size) {
        return audience.list(principal, unreadOnly, page, size);
    }

    @Transactional(readOnly = true)
    public long unreadCount(MemberPrincipal principal) {
        return audience.unreadCount(principal);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public NotificationResponse markRead(MemberPrincipal principal, Long notificationId) {
        Notification notification = repository
                .findById(notificationId)
                .orElseThrow(() -> new NotFoundException("알림을 찾을 수 없습니다: " + notificationId));
        if (!notification.isOwnedBy(principal.username()) || !audience.canRead(principal, notificationId)) {
            throw new NotFoundException("알림을 찾을 수 없습니다: " + notificationId);
        }
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }
}
