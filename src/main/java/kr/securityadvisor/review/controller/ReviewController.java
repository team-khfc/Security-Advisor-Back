package kr.securityadvisor.review.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.review.dto.ReviewRequestCommand;
import kr.securityadvisor.review.dto.ReviewRequestResult;
import kr.securityadvisor.review.dto.ReviewResponse;
import kr.securityadvisor.review.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 협의 API. 협의 요청은 신청의 하위 자원 생성(POST …/reviews)으로 표현한다(AGENTS.md 5장). */
@RestController
@RequestMapping("/api/security-requests/{id}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 협의 요청(1→2단계). 분야별 협의 생성 + 자동 배정 + 협의 기한. 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRequestResult requestReview(
            @CurrentMember MemberPrincipal member, @PathVariable Long id, @Valid @RequestBody ReviewRequestCommand command) {
        return reviewService.requestReview(member, id, command);
    }

    /** 신청의 분야별 협의 목록. */
    @GetMapping
    public List<ReviewResponse> list(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return reviewService.listForRequest(member, id);
    }
}
