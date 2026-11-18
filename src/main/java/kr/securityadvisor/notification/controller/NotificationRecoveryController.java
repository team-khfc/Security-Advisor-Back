package kr.securityadvisor.notification.controller;

import java.util.*;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.notification.service.NotificationRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 관리자 실패 발송 조회·수동 재처리. 실제 주소·본문은 반환하지 않는다. */
@RestController
@RequiredArgsConstructor
public class NotificationRecoveryController {
    private final NotificationRecoveryService service;

    @GetMapping("/api/notification-deliveries")
    public List<Map<String, Object>> list(
            @CurrentMember MemberPrincipal actor, @RequestParam(defaultValue = "0") int page) {
        return service.list(actor, page);
    }

    @PostMapping("/api/notification-deliveries/{id}/retries")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void retry(@CurrentMember MemberPrincipal actor, @PathVariable long id) {
        service.retry(actor, id);
    }
}
