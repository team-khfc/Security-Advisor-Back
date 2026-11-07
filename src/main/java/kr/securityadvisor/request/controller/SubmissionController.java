package kr.securityadvisor.request.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.DocumentBoxItem;
import kr.securityadvisor.request.dto.SubmissionResponse;
import kr.securityadvisor.request.dto.SubmissionSummary;
import kr.securityadvisor.request.dto.SubmitCommand;
import kr.securityadvisor.request.service.SubmissionService;
import kr.securityadvisor.request.service.SubmissionService.SubmitResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 제출·제출본·문서함 API(F-07). 제출은 신청의 하위 자원 생성(POST …/submissions + Idempotency-Key)으로 표현한다. */
@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /** 제출. 새로 만들면 201 + Location, 같은 Idempotency-Key 재요청이면 200 + 기존 제출본. */
    @PostMapping("/api/security-requests/{id}/submissions")
    public ResponseEntity<SubmissionResponse> submit(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitCommand command) {
        SubmitResult result = submissionService.submit(member, id, idempotencyKey, command);
        URI location = URI.create("/api/submissions/" + result.submission().submissionId());
        return result.created()
                ? ResponseEntity.created(location).body(result.submission())
                : ResponseEntity.ok().location(location).body(result.submission());
    }

    @GetMapping("/api/security-requests/{id}/submissions")
    public List<SubmissionSummary> listForRequest(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return submissionService.listForRequest(member, id);
    }

    @GetMapping("/api/submissions/{id}")
    public SubmissionResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return submissionService.get(member, id);
    }

    /** 문서함 — 역할·권한 범위 내 제출본 목록. */
    @GetMapping("/api/submissions")
    public List<DocumentBoxItem> documentBox(
            @CurrentMember MemberPrincipal member,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean latestOnly) {
        return submissionService.documentBox(member, docType, status, latestOnly);
    }
}
