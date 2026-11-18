package kr.securityadvisor.notification.service;

import java.util.*;
import kr.securityadvisor.common.error.*;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 지수형 대기·최대 5회 재시도. 재발송 시 현재 수신 권한을 다시 확인한다. */
@Service
@RequiredArgsConstructor
public class NotificationRecoveryService {
    private final NotificationOutbox outbox;
    private final NotificationRepository notifications;
    private final NotificationAudience audience;
    private final NotificationService service;
    private final ExternalNotificationDispatcher external;

    @Scheduled(fixedDelayString = "${securityadvisor.notification.retry-delay-ms:10000}", initialDelay = 10000)
    public void drain() {
        outbox.expire();
        for (var e : outbox.claim()) {
            try {
                var role = audience.eligibleRole(
                        e.recipient(), audience.requestId(e.link()), NotificationKind.valueOf(e.kind()));
                if (role.isEmpty()) {
                    outbox.finish(e, "SKIPPED");
                    continue;
                }
                if (e.channel().equals("SCREEN")) {
                    if (!service.attempt(new NotificationService.PendingNotification(
                            List.of(e.recipient()),
                            e.eventKey(),
                            NotificationKind.valueOf(e.kind()),
                            e.title(),
                            e.link()))) throw new IllegalStateException();
                } else {
                    var n = notifications.findById(e.notificationId()).orElseThrow();
                    var principal = new MemberPrincipal(e.recipient(), "", MemberRole.valueOf(role.get()));
                    if (!audience.canRead(principal, n.getId())) {
                        outbox.finish(e, "SKIPPED");
                        continue;
                    }
                    external.deliver(e.channel(), n);
                }
                outbox.finish(e, "SENT");
            } catch (RuntimeException error) {
                outbox.failed(e);
            }
        }
    }

    public List<Map<String, Object>> list(MemberPrincipal actor, int page) {
        actor.requireAnyRole(MemberRole.ADMIN);
        if (page < 0 || page > 100000) throw new BadRequestException("페이지 범위가 올바르지 않습니다");
        return outbox.list(page);
    }

    public void retry(MemberPrincipal actor, long id) {
        actor.requireAnyRole(MemberRole.ADMIN);
        if (!outbox.retry(id, actor.username())) throw new ConflictException("실패한 알림만 다시 처리할 수 있습니다");
    }
}
