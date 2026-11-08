package kr.securityadvisor.notification.dto;

import java.time.Instant;
import kr.securityadvisor.notification.domain.Notification;
import kr.securityadvisor.notification.domain.NotificationKind;

/** 알림 응답. */
public record NotificationResponse(
        Long notificationId, NotificationKind kind, String title, String link, Instant readAt, Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getKind(), n.getTitle(), n.getLink(), n.getReadAt(), n.getCreatedAt());
    }
}
