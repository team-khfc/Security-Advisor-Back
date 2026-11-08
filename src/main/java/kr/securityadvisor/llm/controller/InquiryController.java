package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.InquiryCommand;
import kr.securityadvisor.llm.dto.InquiryMessageCommand;
import kr.securityadvisor.llm.dto.InquiryMessageResponse;
import kr.securityadvisor.llm.dto.InquiryResponse;
import kr.securityadvisor.llm.dto.InquiryTurnResult;
import kr.securityadvisor.llm.service.InquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 담당자 AI 질의 API(F-04). 대화는 신청의 하위 자원, 메시지는 대화의 하위 자원이다. */
@RestController
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping("/api/security-requests/{id}/inquiries")
    public ResponseEntity<InquiryResponse> open(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) InquiryCommand command) {
        InquiryResponse response = inquiryService.open(member, id, command);
        return ResponseEntity.created(URI.create("/api/inquiries/" + response.inquiryId()))
                .body(response);
    }

    @GetMapping("/api/security-requests/{id}/inquiries")
    public List<InquiryResponse> listMine(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return inquiryService.listMine(member, id);
    }

    @GetMapping("/api/inquiries/{id}/messages")
    public List<InquiryMessageResponse> messages(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return inquiryService.messages(member, id);
    }

    /** 질문 한 턴. 201 + 질문·답변. */
    @PostMapping("/api/inquiries/{id}/messages")
    public ResponseEntity<InquiryTurnResult> ask(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody InquiryMessageCommand command) {
        InquiryTurnResult result = inquiryService.ask(member, id, command);
        return ResponseEntity.created(URI.create("/api/inquiries/" + id + "/messages"))
                .body(result);
    }
}
