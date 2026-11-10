package kr.securityadvisor.request.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import kr.securityadvisor.assignment.dto.AssignmentResponse;
import kr.securityadvisor.assignment.dto.ReassignRequest;
import kr.securityadvisor.audit.dto.AuditEventResponse;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.AnswerActionRequest;
import kr.securityadvisor.request.dto.AnswerBatchCommand;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.dto.AnswerUpdateRequest;
import kr.securityadvisor.request.dto.DeadlineResponse;
import kr.securityadvisor.request.dto.RequestListQuery;
import kr.securityadvisor.request.dto.RequestListResponse;
import kr.securityadvisor.request.dto.SecurityRequestResponse;
import kr.securityadvisor.request.dto.WithdrawRequest;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.SecurityRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신청 API 중 신청자 행위(조회·서류별 답변·철회). 협의 요청·제출·확정 등 검토 흐름의 전이는 review 컨텍스트 API 가 맡는다.
 */
@RestController
@RequestMapping("/api/security-requests")
@RequiredArgsConstructor
public class SecurityRequestController {

    private final SecurityRequestService securityRequestService;
    private final AnswerService answerService;

    /** 신청 상세(서류별 답변 포함). 남의 신청은 404. */
    @GetMapping("/{id}")
    public SecurityRequestResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return securityRequestService.get(member, id);
    }

    /** 역할별 신청 검색. 날짜는 한국표준시의 생성일이며 정렬은 ID로 동률을 해소한다. */
    @GetMapping
    public RequestListResponse list(
            @CurrentMember MemberPrincipal member,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) Long businessCaseId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "all") String inbox,
            @RequestParam(defaultValue = "all") String documentKind,
            @RequestParam(defaultValue = "all") String supplementState,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return securityRequestService.list(
                member,
                new RequestListQuery(
                        query,
                        status,
                        docType,
                        businessCaseId,
                        from,
                        to,
                        inbox,
                        documentKind,
                        supplementState,
                        sort,
                        page,
                        size));
    }

    /** 서류별 답변 일괄 저장(PATCH). 항목마다 PUT 과 같은 규칙, 한 트랜잭션. */
    @PatchMapping("/{id}/answers")
    public List<AnswerResponse> answerBatch(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody AnswerBatchCommand command) {
        return answerService.answerRequestFields(member, id, command);
    }

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

    /** 배정 이력(활성·비활성). */
    @GetMapping("/{id}/assignments")
    public List<AssignmentResponse> assignments(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return securityRequestService.assignments(member, id);
    }

    /** 재배정(기관 관리자, 사유 필수). 새 배정 생성 — 이전 배정은 비활성으로 보존. 관리자가 아니면 403. */
    @PostMapping("/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse reassign(
            @CurrentMember MemberPrincipal member, @PathVariable Long id, @Valid @RequestBody ReassignRequest request) {
        return securityRequestService.reassign(member, id, request);
    }

    /** 처리 기한(오늘 기준 기한 내·임박·지남 계산 포함). */
    @GetMapping("/{id}/deadlines")
    public List<DeadlineResponse> deadlines(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return securityRequestService.deadlines(member, id);
    }

    /** 처리 이력(감사 이력 중 이 신청 대상). */
    @GetMapping("/{id}/audit-events")
    public List<AuditEventResponse> auditTrail(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return securityRequestService.auditTrail(member, id);
    }
}
