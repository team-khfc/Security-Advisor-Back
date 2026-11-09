package kr.securityadvisor.request.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.FinalDraftCommand;
import kr.securityadvisor.request.dto.FinalDraftResponse;
import kr.securityadvisor.request.service.FinalDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 최종 초안 조회·직접 작성·명시적 사용자 확인. */
@RestController
@RequiredArgsConstructor
public class FinalDraftController {
    private final FinalDraftService service;

    @GetMapping("/api/security-requests/{id}/final-draft")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "현재 최종 초안",
                content = @Content(schema = @Schema(implementation = FinalDraftResponse.class))),
        @ApiResponse(responseCode = "204", description = "아직 작성한 최종 초안 없음", content = @Content)
    })
    public ResponseEntity<FinalDraftResponse> current(@CurrentMember MemberPrincipal actor, @PathVariable Long id) {
        FinalDraftResponse draft = service.current(actor, id);
        return draft == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(draft);
    }

    @PostMapping("/api/security-requests/{id}/final-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FinalDraftResponse> create(
            @CurrentMember MemberPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody FinalDraftCommand command) {
        FinalDraftResponse draft = service.createManual(actor, id, command);
        return ResponseEntity.created(URI.create("/api/security-requests/" + id + "/final-draft"))
                .body(draft);
    }

    @PutMapping("/api/final-drafts/{id}/confirmation")
    public FinalDraftResponse confirm(
            @CurrentMember MemberPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody FinalDraftCommand command) {
        return service.confirm(actor, id, command);
    }
}
