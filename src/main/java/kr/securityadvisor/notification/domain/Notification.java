package kr.securityadvisor.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;

/** 수신자 1명에게 가는 알림. 읽음 처리만 바뀐다(업무 상태와 무관, 불변식 30). */
@Entity
@Table(name = "notifications")
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "recipient_username", nullable = false, length = 50)
    private String recipientUsername;

    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationKind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 300)
    private String link;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    private Notification(String recipientUsername, String eventKey, NotificationKind kind, String title, String link) {
        this.recipientUsername = recipientUsername;
        this.eventKey = eventKey;
        this.kind = kind;
        this.title = title;
        this.link = link;
    }

    public static Notification of(
            String recipientUsername, String eventKey, NotificationKind kind, String title, String link) {
        if (recipientUsername == null
                || recipientUsername.isBlank()
                || eventKey == null
                || eventKey.isBlank()
                || kind == null) {
            throw new DomainRuleViolationException("알림에는 수신자·사건 키·종류가 필요합니다");
        }
        if (title == null || title.isBlank()) {
            throw new DomainRuleViolationException("알림 제목이 필요합니다");
        }
        return new Notification(
                recipientUsername, eventKey, kind, title.length() > 200 ? title.substring(0, 200) : title, link);
    }

    public boolean isOwnedBy(String username) {
        return recipientUsername.equals(username);
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public String getEventKey() {
        return eventKey;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getLink() {
        return link;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
