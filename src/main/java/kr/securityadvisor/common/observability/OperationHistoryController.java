package kr.securityadvisor.common.observability;

import kr.securityadvisor.common.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 브라우저에는 관리자용 보존 지표만 전달하고 Actuator 원본을 프록시하지 않는다. */
@RestController
@RequiredArgsConstructor
public class OperationHistoryController {
    private final OperationHistory service;

    @GetMapping("/api/operation-history")
    public OperationHistory.History history(
            @CurrentMember MemberPrincipal actor, @RequestParam(defaultValue = "0") int page) {
        return service.history(actor, page);
    }

    @PostMapping("/api/operation-alerts/{id}/acknowledgements")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void acknowledge(@CurrentMember MemberPrincipal actor, @PathVariable long id) {
        service.acknowledge(actor, id);
    }
}
