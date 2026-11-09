package kr.securityadvisor.request.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.AddSecurityRequestCommand;
import kr.securityadvisor.request.dto.AnswerActionRequest;
import kr.securityadvisor.request.dto.AnswerBatchCommand;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.dto.AnswerUpdateRequest;
import kr.securityadvisor.request.dto.BusinessCaseListItem;
import kr.securityadvisor.request.dto.BusinessCaseResponse;
import kr.securityadvisor.request.dto.CreateBusinessCaseRequest;
import kr.securityadvisor.request.dto.SecurityRequestSummary;
import kr.securityadvisor.request.dto.UpdateBusinessCaseRequest;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.BusinessCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 사업 건 API(F-01·F-03 공통 답변). 경로는 자원 명사, 답변은 필드 키 단위 하위 자원(PUT, 멱등). */
@RestController
@RequestMapping("/api/business-cases")
@RequiredArgsConstructor
public class BusinessCaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final BusinessCaseService businessCaseService;
    private final AnswerService answerService;

    /** 사업 건 + 선택한 서류 유형별 신청 생성. 201 + Location. */
    @PostMapping
    public ResponseEntity<BusinessCaseResponse> create(
            @CurrentMember MemberPrincipal member, @Valid @RequestBody CreateBusinessCaseRequest request) {
        BusinessCaseResponse created = businessCaseService.create(member, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.businessCaseId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** 내 사업 건 목록. page 는 0부터, size 는 최대 100. */
    @GetMapping
    public PageResponse<BusinessCaseListItem> list(
            @CurrentMember MemberPrincipal member,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("createdAt").descending());
        return businessCaseService.listMine(member, pageable);
    }

    /** 사업 건 상세. 남의 사업 건은 404. */
    @GetMapping("/{id}")
    public BusinessCaseResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return businessCaseService.get(member, id);
    }

    /** 사업 개요 수정(revision 검사). */
    /** 기존 사업 건에 서류 추가(F-01 P2). 공통 답변·문서를 공유하는 새 신청이 1단계로 생긴다. */
    @PostMapping("/{id}/security-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public SecurityRequestSummary addRequest(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody AddSecurityRequestCommand command) {
        return businessCaseService.addRequest(member, id, command);
    }

    @PutMapping("/{id}")
    public BusinessCaseResponse update(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody UpdateBusinessCaseRequest request) {
        return businessCaseService.updateOutline(member, id, request);
    }

    /** 공통 항목 답변 저장(사용자 답변). */
    /** 공통 답변 일괄 저장(PATCH). 항목마다 PUT 과 같은 규칙, 한 트랜잭션. */
    @PatchMapping("/{id}/answers")
    public List<AnswerResponse> answerBatch(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody AnswerBatchCommand command) {
        return answerService.answerCaseFields(member, id, command);
    }

    @PutMapping("/{id}/answers/{fieldKey}")
    public AnswerResponse answer(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @Valid @RequestBody AnswerUpdateRequest request) {
        return answerService.answerCaseField(member, id, fieldKey, request);
    }

    /** 공통 항목의 AI 제안 값을 사용자가 확인. */
    @PutMapping("/{id}/answers/{fieldKey}/confirmation")
    public AnswerResponse confirm(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @Valid @RequestBody AnswerActionRequest request) {
        return answerService.confirmCaseAnswer(member, id, fieldKey, request);
    }

    /** 공통 항목을 담당자 확인 요청으로 넘김. */
    @PutMapping("/{id}/answers/{fieldKey}/staff-check")
    public AnswerResponse requestStaffCheck(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @Valid @RequestBody AnswerActionRequest request) {
        return answerService.requestStaffCheckOnCase(member, id, fieldKey, request);
    }
}
