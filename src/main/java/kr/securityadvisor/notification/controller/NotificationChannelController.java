package kr.securityadvisor.notification.controller;

import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.notification.service.ExternalNotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 외부 알림 채널(메일·메신저) 설정 상태 — 기관 관리자. 설정값(URL·비밀번호)은 내보내지 않고 켜짐 여부만. */
@RestController
@RequiredArgsConstructor
public class NotificationChannelController {

    private final ExternalNotificationDispatcher dispatcher;

    @GetMapping("/api/notification-channels")
    public List<ExternalNotificationDispatcher.ChannelStatus> channels(@CurrentMember MemberPrincipal admin) {
        admin.requireAnyRole(MemberRole.ADMIN);
        return dispatcher.status();
    }
}
