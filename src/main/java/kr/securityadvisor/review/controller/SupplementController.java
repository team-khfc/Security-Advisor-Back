package kr.securityadvisor.review.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.review.dto.SupplementAnswerCommand;
import kr.securityadvisor.review.dto.SupplementRequestCommand;
import kr.securityadvisor.review.dto.SupplementResponse;
import kr.securityadvisor.review.service.SupplementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 보완 요청·답변 API(F-05). 요청은 협의의 하위 자원 생성, 답변은 보완 요청의 하위 자원 생성으로 표현한다. */
@RestController
@RequiredArgsConstructor
public class SupplementController {

    private final SupplementService supplementService;

    /** 담당자의 보완 요청. 201 + Location. */
    @PostMapping("/api/reviews/{id}/supplements")
    public ResponseEntity<SupplementResponse> request(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody SupplementRequestCommand command) {
        SupplementResponse response = supplementService.request(member, id, command);
        return ResponseEntity.created(URI.create("/api/supplements/" + response.supplementId()))
                .body(response);
    }

    /** 신청자의 보완 답변. 201. */
    @PostMapping("/api/supplements/{id}/answers")
    public ResponseEntity<SupplementResponse> answer(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody SupplementAnswerCommand command) {
        SupplementResponse response = supplementService.answer(member, id, command);
        return ResponseEntity.created(URI.create("/api/supplements/" + id)).body(response);
    }

    /** 담당자 해소(그 분야 배정자). 답변된 보완만, 멱등. */
    @PutMapping("/api/supplements/{id}/resolution")
    public SupplementResponse resolve(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody SupplementAnswerCommand command) {
        return supplementService.resolve(member, id, command);
    }

    /** 신청의 보완 요청 목록. */
    @GetMapping("/api/security-requests/{id}/supplements")
    public List<SupplementResponse> list(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return supplementService.listForRequest(member, id);
    }
}
