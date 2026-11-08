package kr.securityadvisor.notification.service;

import java.util.List;
import kr.securityadvisor.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 저장된 알림을 켜진 외부 채널들로 비동기 전달한다. 채널 실패는 로그만(불변식 30). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalNotificationDispatcher {

    private final List<NotificationChannel> channels;

    @Async
    public void dispatch(Notification notification) {
        for (NotificationChannel channel : channels) {
            if (!channel.enabled()) {
                continue;
            }
            try {
                channel.deliver(notification);
            } catch (RuntimeException e) {
                log.warn(
                        "외부 알림 전달 실패 channel={} notificationId={} ({})",
                        channel.name(),
                        notification.getId(),
                        e.getClass().getSimpleName());
            }
        }
    }

    /** 채널별 설정 상태(관리자 조회용). */
    public List<ChannelStatus> status() {
        return channels.stream()
                .map(c -> new ChannelStatus(c.name(), c.enabled()))
                .toList();
    }

    public record ChannelStatus(String name, boolean enabled) {}
}
