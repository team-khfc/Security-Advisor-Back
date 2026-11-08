package kr.securityadvisor.notification.service;

import java.util.Collection;
import kr.securityadvisor.notification.domain.NotificationKind;

/**
 * 다른 컨텍스트가 쓰는 알림 발송 인터페이스. 실패해도 예외를 던지지 않는다(불변식 30 — 알림 실패가 업무를 실패시키지 않는다).
 * 제목·링크에 값·비밀값을 넣지 않고 참조 ID 만 쓴다.
 */
public interface Notifier {

    void notify(Collection<String> recipients, String eventKey, NotificationKind kind, String title, String link);

    default void notify(String recipient, String eventKey, NotificationKind kind, String title, String link) {
        notify(java.util.List.of(recipient), eventKey, kind, title, link);
    }
}
