package kr.securityadvisor.document.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.schema.ExportFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 다운로드. format 은 MARKDOWN(기본)·PDF 등 {@link ExportFormat}. 허용되지 않은 형식 422, 미구현 형식 503. */
    @GetMapping("/api/generated-documents/{id}/download")
    public ResponseEntity<byte[]> download(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @RequestParam(defaultValue = "MARKDOWN") String format) {
        ExportFormat exportFormat =
                ExportFormat.parse(format).orElseThrow(() -> new BadRequestException("알 수 없는 출력 형식입니다: " + format));
        DocumentService.Download download = documentService.download(member, id, exportFormat);
        String encoded =
                URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(download.format().mediaType()))
                .body(download.bytes());
    }

    /** 출력 형식 목록(구현 여부 포함). 서류 유형별 허용 형식은 게시본 docTypes.exportFormats 에 있다. */
    @GetMapping("/api/document-export-formats")
    public List<DocumentService.ExportFormatInfo> exportFormats() {
        return documentService.exportFormats();
    }
}
