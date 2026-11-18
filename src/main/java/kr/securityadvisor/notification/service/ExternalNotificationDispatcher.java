package kr.securityadvisor.notification.service;

import java.util.List;
import kr.securityadvisor.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 저장된 알림을 켜진 외부 채널들로 비동기 전달한다. 채널 실패는 로그만(불변식 30). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalNotificationDispatcher {

    private final List<NotificationChannel> channels;
    private final kr.securityadvisor.common.observability.OperationMetrics metrics;

    private final kr.securityadvisor.notification.repository.NotificationOutbox outbox;

    public void dispatch(Notification notification) {
        for (NotificationChannel channel : channels) {
            if (channel.enabled())
                outbox.enqueue(
                        notification.getRecipientUsername(),
                        notification.getEventKey(),
                        channel.name(),
                        notification.getKind().name(),
                        notification.getTitle(),
                        notification.getLink(),
                        notification.getId());
        }
    }

    public void deliver(String name, Notification notification) {
        var channel =
                channels.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
        if (!channel.enabled()) throw new IllegalStateException("알림 채널이 비활성 상태입니다");
        metrics.measure(kr.securityadvisor.common.observability.OperationMetrics.Operation.NOTIFICATION, name, () -> {
            channel.deliver(notification);
            return true;
        });
    }

    /** 채널별 설정 상태(관리자 조회용). */
    public List<ChannelStatus> status() {
        return channels.stream()
                .map(c -> new ChannelStatus(c.name(), c.enabled()))
                .toList();
    }

    public record ChannelStatus(String name, boolean enabled) {}
}
