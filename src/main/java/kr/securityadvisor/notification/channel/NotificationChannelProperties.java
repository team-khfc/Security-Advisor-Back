package kr.securityadvisor.notification.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 알림 채널 설정(F-17 P3). 전부 환경변수로 받고, 값이 비어 있으면 그 채널은 꺼진다.
 *
 * @param webhook 메신저 수신 웹훅(Slack·Teams·카카오워크 등 "incoming webhook" 형식 — JSON {"text": …})
 * @param smtp 메일 서버(기관 내부 릴레이 전제)
 */
@ConfigurationProperties(prefix = "securityadvisor.notification")
public record NotificationChannelProperties(Webhook webhook, Smtp smtp) {

    public NotificationChannelProperties {
        webhook = webhook == null ? new Webhook(null, 5) : webhook;
        smtp = smtp == null ? new Smtp(null, 25, false, null, null, null, 10) : smtp;
    }

    public record Webhook(String url, int timeoutSeconds) {
        public boolean enabled() {
            return url != null && !url.isBlank();
        }
    }

    /**
     * @param password 환경변수 값(로그·응답에 절대 내보내지 않는다)
     * @param from 보내는 주소
     */
    public record Smtp(
            String host,
            int port,
            boolean starttls,
            String username,
            String password,
            String from,
            int timeoutSeconds) {
        public boolean enabled() {
            return host != null && !host.isBlank() && from != null && !from.isBlank();
        }
    }
}
