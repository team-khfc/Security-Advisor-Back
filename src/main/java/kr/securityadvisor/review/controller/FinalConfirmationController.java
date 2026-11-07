package kr.securityadvisor.review.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.review.dto.ConfirmationCommand;
import kr.securityadvisor.review.dto.FinalConfirmationCommand;
import kr.securityadvisor.review.dto.FinalConfirmationResponse;
import kr.securityadvisor.review.service.FinalConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 4단계 최종 확인(하위 자원 생성 POST …/final-confirmations)·확정(단일 하위 자원 설정 PUT …/confirmation) API. */
@RestController
@RequestMapping("/api/submissions/{id}")
@RequiredArgsConstructor
public class FinalConfirmationController {

    private final FinalConfirmationService finalConfirmationService;

    @PostMapping("/final-confirmations")
    public ResponseEntity<FinalConfirmationResponse> confirmFinal(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody FinalConfirmationCommand command) {
        FinalConfirmationResponse response = finalConfirmationService.confirmFinal(member, id, command);
        return ResponseEntity.created(URI.create("/api/submissions/" + id + "/final-confirmations"))
                .body(response);
    }

    @GetMapping("/final-confirmations")
    public List<FinalConfirmationResponse> list(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return finalConfirmationService.list(member, id);
    }

    @PutMapping("/confirmation")
    public SecurityRequestSummary confirm(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody ConfirmationCommand command) {
        return finalConfirmationService.confirm(member, id, command);
    }
}
