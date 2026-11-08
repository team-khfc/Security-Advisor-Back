package kr.securityadvisor.notification.repository;

import java.util.List;
import kr.securityadvisor.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

/** 알림 저장소. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUsernameOrderByIdDesc(String recipientUsername);

    List<Notification> findByRecipientUsernameAndReadAtIsNullOrderByIdDesc(String recipientUsername);

    boolean existsByRecipientUsernameAndEventKey(String recipientUsername, String eventKey);

    long countByRecipientUsernameAndReadAtIsNull(String recipientUsername);
}
