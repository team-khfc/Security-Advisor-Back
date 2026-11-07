package kr.securityadvisor.request.controller;

import jakarta.validation.Valid;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.AnswerActionRequest;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.dto.AnswerUpdateRequest;
import kr.securityadvisor.request.dto.SecurityRequestResponse;
import kr.securityadvisor.request.dto.WithdrawRequest;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.SecurityRequestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신청 API 중 신청자 행위(조회·서류별 답변·철회). 협의 요청·제출·확정 등 검토 흐름의 전이는 review 컨텍스트 API 가 맡는다.
 */
@RestController
@RequestMapping("/api/security-requests")
public class SecurityRequestController {

    private final SecurityRequestService securityRequestService;
    private final AnswerService answerService;

    public SecurityRequestController(SecurityRequestService securityRequestService, AnswerService answerService) {
        this.securityRequestService = securityRequestService;
        this.answerService = answerService;
    }

    /** 신청 상세(서류별 답변 포함). 남의 신청은 404. */
    @GetMapping("/{id}")
    public SecurityRequestResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return securityRequestService.get(member, id);
    }

    /** 서류별 항목 답변 저장(사용자 답변). */
    @PutMapping("/{id}/answers/{fieldKey}")
    public AnswerResponse answer(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @Valid @RequestBody AnswerUpdateRequest request) {
        return answerService.answerRequestField(member, id, fieldKey, request);
    }

    /** 서류별 항목의 AI 제안 값을 사용자가 확인. */
    @PutMapping("/{id}/answers/{fieldKey}/confirmation")
    public AnswerResponse confirm(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @Valid @RequestBody AnswerActionRequest request) {
        return answerService.confirmRequestAnswer(member, id, fieldKey, request);
    }

    /** 서류별 항목을 담당자 확인 요청으로 넘김. */
    @PutMapping("/{id}/answers/{fieldKey}/staff-check")
    public AnswerResponse requestStaffCheck(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @Valid @RequestBody AnswerActionRequest request) {
        return answerService.requestStaffCheckOnRequest(member, id, fieldKey, request);
    }

    /** 신청 철회(단일 하위 자원 설정, 사유 필수). 종결·철회·확정된 신청은 422. */
    @PutMapping("/{id}/withdrawal")
    public SecurityRequestResponse withdraw(
            @CurrentMember MemberPrincipal member, @PathVariable Long id, @Valid @RequestBody WithdrawRequest request) {
        return securityRequestService.withdraw(member, id, request);
    }
}
