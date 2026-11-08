package kr.securityadvisor.llm.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.AnalysisJobCommand;
import kr.securityadvisor.llm.dto.AnalysisJobResponse;
import kr.securityadvisor.llm.service.DocumentAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 문서 AI 요약 API(F-02). 분석 요청은 202 + Location(작업 자원), 상태는 작업 조회로 확인한다. */
@RestController
@RequiredArgsConstructor
public class AnalysisJobController {

    private final DocumentAnalysisService service;

    @PostMapping("/api/business-cases/{id}/analysis-jobs")
    public ResponseEntity<AnalysisJobResponse> request(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody AnalysisJobCommand command) {
        AnalysisJobResponse response = service.request(member, id, command);
        return ResponseEntity.accepted()
                .location(URI.create("/api/analysis-jobs/" + response.analysisJobId()))
                .body(response);
    }

    @GetMapping("/api/business-cases/{id}/analysis-jobs")
    public List<AnalysisJobResponse> list(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.listForCase(member, id);
    }

    @GetMapping("/api/analysis-jobs/{id}")
    public AnalysisJobResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.get(member, id);
    }
}
