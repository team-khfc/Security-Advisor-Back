package kr.securityadvisor.notification.controller;

import jakarta.validation.Valid;
import java.util.Map;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.notification.dto.NotificationReadCommand;
import kr.securityadvisor.notification.dto.NotificationResponse;
import kr.securityadvisor.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 API(F-17). 수신자 본인만. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @CurrentMember MemberPrincipal member,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(member, unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@CurrentMember MemberPrincipal member) {
        return Map.of("unread", service.unreadCount(member));
    }

    @PatchMapping("/{id}")
    public NotificationResponse markRead(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody NotificationReadCommand command) {
        return service.markRead(member, id);
    }
}
