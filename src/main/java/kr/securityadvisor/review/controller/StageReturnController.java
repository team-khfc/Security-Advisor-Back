package kr.securityadvisor.review.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.review.dto.ClosureCommand;
import kr.securityadvisor.review.dto.StageReturnCommand;
import kr.securityadvisor.review.dto.StageReturnResponse;
import kr.securityadvisor.review.service.StageReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 차수 반환(하위 자원 생성 POST …/stage-returns)·종결(단일 하위 자원 설정 PUT …/closure) API — status 직접 PATCH 금지. */
@RestController
@RequestMapping("/api/security-requests/{id}")
@RequiredArgsConstructor
public class StageReturnController {

    private final StageReturnService stageReturnService;

    @PostMapping("/stage-returns")
    public ResponseEntity<StageReturnResponse> returnToStage(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody StageReturnCommand command) {
        StageReturnResponse response = stageReturnService.returnToStage(member, id, command);
        return ResponseEntity.created(URI.create("/api/security-requests/" + id + "/stage-returns"))
                .body(response);
    }

    @GetMapping("/stage-returns")
    public List<StageReturnResponse> list(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return stageReturnService.listForRequest(member, id);
    }

    /** 종결(반려). 사유 필수. */
    @PutMapping("/closure")
    public SecurityRequestSummary close(
            @CurrentMember MemberPrincipal member, @PathVariable Long id, @Valid @RequestBody ClosureCommand command) {
        return stageReturnService.close(member, id, command);
    }
}
