package kr.securityadvisor.notification.channel;

import java.time.Duration;
import java.util.Map;
import kr.securityadvisor.notification.domain.Notification;
import kr.securityadvisor.notification.service.NotificationChannel;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 메신저 웹훅 채널: 수신 웹훅 URL 하나로 기관 메신저 채널에 한 줄 알림을 보낸다(수신자 구분 없음 — 채널 공지). */
@Component
public class WebhookNotificationChannel implements NotificationChannel {

    private final NotificationChannelProperties.Webhook settings;
    private final RestClient client;

    public WebhookNotificationChannel(NotificationChannelProperties properties) {
        this.settings = properties.webhook();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(settings.timeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(settings.timeoutSeconds()));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "WEBHOOK";
    }

    @Override
    public boolean enabled() {
        return settings.enabled();
    }

    @Override
    public void deliver(Notification notification) {
        client.post()
                .uri(settings.url())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text(notification)))
                .retrieve()
                .toBodilessEntity();
    }

    /** 본문: 제목 + 수신자 아이디 + 링크. 값은 넣지 않는다. */
    static String text(Notification n) {
        return "[보안길잡이] " + n.getTitle() + " (대상: " + n.getRecipientUsername() + ")" + " " + n.getLink();
    }
}
