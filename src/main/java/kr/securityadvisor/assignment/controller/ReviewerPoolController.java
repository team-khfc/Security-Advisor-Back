package kr.securityadvisor.assignment.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.assignment.dto.ReviewerPoolActivationCommand;
import kr.securityadvisor.assignment.dto.ReviewerPoolDeactivationResult;
import kr.securityadvisor.assignment.dto.ReviewerPoolRegisterCommand;
import kr.securityadvisor.assignment.dto.ReviewerPoolResponse;
import kr.securityadvisor.assignment.service.ReviewerPoolService;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 분야별 담당자 목록 API(F-12, 기관 관리자). 기본 담당자·활성 여부는 항목의 하위 자원(PUT …/default, PUT …/activation)으로
 * 표현한다(AGENTS.md 5장). 삭제는 없다 — 비활성화만.
 */
@RestController
@RequestMapping("/api/reviewer-pools")
@RequiredArgsConstructor
public class ReviewerPoolController {

    private final ReviewerPoolService reviewerPoolService;

    @GetMapping
    public List<ReviewerPoolResponse> list(
            @CurrentMember MemberPrincipal admin, @RequestParam(required = false) ReviewField field) {
        return reviewerPoolService.list(admin, field);
    }

    @PostMapping
    public ResponseEntity<ReviewerPoolResponse> register(
            @CurrentMember MemberPrincipal admin, @Valid @RequestBody ReviewerPoolRegisterCommand command) {
        ReviewerPoolResponse created = reviewerPoolService.register(admin, command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.reviewerPoolId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** 기본 담당자 지정. 이전 기본 담당자는 자동 해제. */
    @PutMapping("/{id}/default")
    public ReviewerPoolResponse setDefault(@CurrentMember MemberPrincipal admin, @PathVariable Long id) {
        return reviewerPoolService.setDefault(admin, id);
    }

    /** 활성/비활성 설정. 비활성화 응답에 재배정 필요 신청 ID 가 담긴다. */
    @PutMapping("/{id}/activation")
    public ReviewerPoolDeactivationResult setActivation(
            @CurrentMember MemberPrincipal admin,
            @PathVariable Long id,
            @Valid @RequestBody ReviewerPoolActivationCommand command) {
        return reviewerPoolService.setActivation(admin, id, command);
    }
}
