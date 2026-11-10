package kr.securityadvisor.review.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.AnswerBatchCommand;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.review.dto.ReviewCompletionCommand;
import kr.securityadvisor.review.dto.ReviewInboxFilter;
import kr.securityadvisor.review.dto.ReviewInboxItem;
import kr.securityadvisor.review.dto.ReviewResponse;
import kr.securityadvisor.review.dto.ReviewRevisionCommand;
import kr.securityadvisor.review.dto.StaffAnswerCommand;
import kr.securityadvisor.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 담당자 검토 API(F-04). 협의를 자원으로 두고 할당 완료·협의 완료는 단일 하위 자원 설정(PUT …/allocation-completion, PUT …/completion)으로,
 * 담당자 값 입력·확인은 협의 아래 항목 자원(PUT …/answers/{fieldKey}, PUT …/answers/{fieldKey}/confirmation)으로 표현한다(AGENTS.md 5장).
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewActionController {

    private final ReviewService reviewService;

    /** 담당자 요청함 — 내게 배정된 신청의 협의 목록. status·requestStatus·field·docType·keyword 로 거른다(모두 선택). */
    @GetMapping
    public List<ReviewInboxItem> inbox(
            @CurrentMember MemberPrincipal member,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestStatus,
            @RequestParam(required = false) ReviewField field,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String keyword) {
        return reviewService.inbox(member, ReviewInboxFilter.of(status, requestStatus, field, docType, keyword));
    }

    @GetMapping("/{id}")
    public ReviewResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return reviewService.get(member, id);
    }

    /** 담당자 입력(STAFF_INPUT). 멱등: 같은 값을 다시 넣어도 결과가 같다. */
    @PutMapping("/{id}/answers/{fieldKey}")
    public AnswerResponse inputAnswer(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @Valid @RequestBody StaffAnswerCommand command) {
        return reviewService.inputAnswer(member, id, fieldKey, command);
    }

    /** 담당자 일괄 입력. 항목 하나라도 실패하면 모든 변경을 취소한다. */
    @PatchMapping("/{id}/answers")
    public List<AnswerResponse> inputAnswers(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody AnswerBatchCommand command) {
        return reviewService.inputAnswers(member, id, command);
    }

    /** 담당자 확인(STAFF_CONFIRMED). */
    @PutMapping("/{id}/answers/{fieldKey}/confirmation")
    public AnswerResponse confirmAnswer(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @Valid @RequestBody ReviewRevisionCommand command) {
        return reviewService.confirmAnswer(member, id, fieldKey, command);
    }

    /** INFRA 할당 완료(협의 완료 아님). */
    @PutMapping("/{id}/allocation-completion")
    public ReviewResponse completeAllocation(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody ReviewRevisionCommand command) {
        return reviewService.completeAllocation(member, id, command);
    }

    /** 분야 협의 완료. 두 분야 모두 완료되면 신청이 3단계로 넘어간다. */
    @PutMapping("/{id}/completion")
    public ReviewResponse complete(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody ReviewCompletionCommand command) {
        return reviewService.complete(member, id, command);
    }
}
