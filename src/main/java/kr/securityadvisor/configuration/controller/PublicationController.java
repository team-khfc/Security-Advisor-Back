package kr.securityadvisor.configuration.controller;

import lombok.RequiredArgsConstructor;
import kr.securityadvisor.configuration.dto.PublicationResponse;
import kr.securityadvisor.configuration.service.PublicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기관 설정 게시본 API. 1차 범위는 조회만(편집·게시 F-08 은 P2). */
@RestController
@RequestMapping("/api/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;

    /** 현재 게시본 — 새 사업 건의 인터뷰 질문 구성에 쓴다. */
    @GetMapping("/current")
    public PublicationResponse current() {
        return publicationService.getCurrent();
    }
}
