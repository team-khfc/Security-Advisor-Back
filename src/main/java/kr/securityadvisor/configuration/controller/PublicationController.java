package kr.securityadvisor.configuration.controller;

import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.dto.ConfigurationComparisonResponse;
import kr.securityadvisor.configuration.dto.PublicationResponse;
import kr.securityadvisor.configuration.dto.PublicationSummaryResponse;
import kr.securityadvisor.configuration.service.PublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기관 설정 게시본과 관리자용 게시 이력 API. */
@RestController
@RequestMapping("/api/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;

    /** 관리자용 게시 이력. */
    @GetMapping
    public PageResponse<PublicationSummaryResponse> history(
            @CurrentMember MemberPrincipal member,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return publicationService.history(member, page, size);
    }

    /** 게시 당시 변경 내역. */
    @GetMapping("/{id}/comparison")
    public ConfigurationComparisonResponse comparison(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return publicationService.comparison(member, id);
    }

    /** 현재 게시본 — 새 사업 건의 인터뷰 질문 구성에 쓴다. */
    @GetMapping("/current")
    public PublicationResponse current() {
        return publicationService.getCurrent();
    }

    /** 게시본 하나(사업 건이 고정한 버전 조회용). */
    @GetMapping("/{id}")
    public PublicationResponse get(@PathVariable Long id) {
        return publicationService.get(id);
    }
}
