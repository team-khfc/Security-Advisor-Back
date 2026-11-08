package kr.securityadvisor.notification.channel;

import kr.securityadvisor.auth.service.MemberDirectory;
import kr.securityadvisor.notification.domain.Notification;
import kr.securityadvisor.notification.service.NotificationChannel;
import org.springframework.stereotype.Component;

/** 메일 채널: 수신자 계정에 메일 주소가 있을 때만 보낸다. 본문은 제목·링크 안내뿐(값 없음). */
@Component
public class SmtpEmailChannel implements NotificationChannel {

    private final NotificationChannelProperties.Smtp settings;
    private final MemberDirectory memberDirectory;
    private final SmtpClient client;

    public SmtpEmailChannel(NotificationChannelProperties properties, MemberDirectory memberDirectory) {
        this.settings = properties.smtp();
        this.memberDirectory = memberDirectory;
        this.client = new SmtpClient(settings);
    }

    @Override
    public String name() {
        return "EMAIL";
    }

    @Override
    public boolean enabled() {
        return settings.enabled();
    }

    @Override
    public void deliver(Notification notification) {
        memberDirectory
                .emailOf(notification.getRecipientUsername())
                .ifPresent(to -> client.send(
                        to,
                        "[보안길잡이] " + notification.getTitle(),
                        notification.getTitle() + "\n\n보안길잡이에 로그인해 확인하세요: " + notification.getLink()
                                + "\n\n(이 메일에는 신청 내용·내부 정보가 포함되지 않습니다)"));
    }
}
