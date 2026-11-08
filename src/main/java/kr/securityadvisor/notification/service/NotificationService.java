package kr.securityadvisor.notification.service;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.notification.domain.Notification;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.dto.NotificationResponse;
import kr.securityadvisor.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 화면 안 알림(F-17). 발송은 별도 트랜잭션에서 하고 실패는 로그만 남긴다. 조회·읽음은 수신자 본인만. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements Notifier {

    private final NotificationRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public void notify(
            Collection<String> recipients, String eventKey, NotificationKind kind, String title, String link) {
        if (recipients == null) {
            return;
        }
        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            try {
                // 업무 트랜잭션과 분리(REQUIRES_NEW): 알림 저장 실패가 업무 변경을 롤백시키지 않는다(불변식 30)
                TransactionTemplate separate = new TransactionTemplate(transactionTemplate.getTransactionManager());
                separate.setPropagationBehavior(
                        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                separate.executeWithoutResult(status -> {
                    if (!repository.existsByRecipientUsernameAndEventKey(recipient, eventKey)) {
                        repository.save(Notification.of(recipient, eventKey, kind, title, link));
                    }
                });
            } catch (RuntimeException e) {
                log.warn(
                        "알림 생성 실패 recipient={} eventKey={} ({})",
                        recipient,
                        eventKey,
                        e.getClass().getSimpleName());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(MemberPrincipal principal, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? repository.findByRecipientUsernameAndReadAtIsNullOrderByIdDesc(principal.username())
                : repository.findByRecipientUsernameOrderByIdDesc(principal.username());
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(MemberPrincipal principal) {
        return repository.countByRecipientUsernameAndReadAtIsNull(principal.username());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public NotificationResponse markRead(MemberPrincipal principal, Long notificationId) {
        Notification notification = repository
                .findById(notificationId)
                .orElseThrow(() -> new NotFoundException("알림을 찾을 수 없습니다: " + notificationId));
        if (!notification.isOwnedBy(principal.username())) {
            throw new NotFoundException("알림을 찾을 수 없습니다: " + notificationId);
        }
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }
}
