package kr.securityadvisor.document.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.document.dto.GenerateDocumentCommand;
import kr.securityadvisor.document.dto.GeneratedDocumentResponse;
import kr.securityadvisor.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 서류 API(F-06·F-07 문서함 다운로드). 생성은 신청의 하위 자원, 다운로드는 서류의 표현(.md). */
@RestController
@RequiredArgsConstructor
public class GeneratedDocumentController {

    private final DocumentService documentService;

    @PostMapping("/api/security-requests/{id}/generated-documents")
    public ResponseEntity<GeneratedDocumentResponse> generate(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @RequestBody(required = false) GenerateDocumentCommand command) {
        GeneratedDocumentResponse response = documentService.generate(member, id, command);
        return ResponseEntity.created(URI.create("/api/generated-documents/" + response.generatedDocumentId()))
                .body(response);
    }

    @GetMapping("/api/security-requests/{id}/generated-documents")
    public List<GeneratedDocumentResponse> list(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return documentService.listForRequest(member, id);
    }

    @GetMapping("/api/generated-documents/{id}")
    public GeneratedDocumentResponse get(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return documentService.get(member, id);
    }

    @GetMapping("/api/generated-documents/{id}/download")
    public ResponseEntity<byte[]> download(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        DocumentService.Download download = documentService.download(member, id);
        String encoded =
                URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(download.bytes());
    }
}
