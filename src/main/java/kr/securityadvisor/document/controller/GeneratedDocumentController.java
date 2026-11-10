package kr.securityadvisor.document.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.schema.ExportFormat;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.document.dto.GenerateDocumentCommand;
import kr.securityadvisor.document.dto.GenerateFinalDraftCommand;
import kr.securityadvisor.document.dto.GeneratedDocumentResponse;
import kr.securityadvisor.document.service.DocumentService;
import kr.securityadvisor.document.service.FinalDraftGenerationService;
import kr.securityadvisor.request.dto.FinalDraftResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 서류 API(F-06·F-07 문서함 다운로드). 생성은 신청의 하위 자원, 다운로드는 서류의 표현(.md). */
@RestController
@RequiredArgsConstructor
public class GeneratedDocumentController {

    private final DocumentService documentService;
    private final FinalDraftGenerationService finalDraftGeneration;

    /** 제출 가능한 최종 서술을 만들되 사람 확인은 별도 요청으로 받는다. */
    @PostMapping("/api/security-requests/{id}/final-draft-generations")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FinalDraftResponse> generateFinalDraft(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody GenerateFinalDraftCommand command) {
        FinalDraftResponse draft = finalDraftGeneration.generate(member, id, command.expectedRevision());
        return ResponseEntity.created(URI.create("/api/security-requests/" + id + "/final-draft"))
                .body(draft);
    }

    @PostMapping("/api/security-requests/{id}/generated-documents")
    public ResponseEntity<GeneratedDocumentResponse> generate(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @RequestBody(required = false) GenerateDocumentCommand command) {
        GeneratedDocumentResponse response = documentService.generate(member, id, command);
        return ResponseEntity.created(URI.create("/api/generated-documents/" + response.generatedDocumentId()))
                .body(response);
    }

    /** AI 보완 초안 생성 별칭(화면 호환): generated-documents 에 aiSupplement=true 로 생성하는 것과 같다. */
    @PostMapping("/api/security-requests/{id}/draft-generations")
    public ResponseEntity<GeneratedDocumentResponse> generateDraft(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @RequestBody(required = false) GenerateDocumentCommand command) {
        GenerateDocumentCommand withAi =
                new GenerateDocumentCommand(command == null ? null : command.submissionId(), true);
        GeneratedDocumentResponse response = documentService.generate(member, id, withAi);
        return ResponseEntity.created(URI.create("/api/generated-documents/" + response.generatedDocumentId()))
                .body(response);
    }

    /**
     * 서류 데이터(JSON) 내려받기 — 현재 답변 또는 `submissionId` 제출본 스냅샷으로 렌더링한 섹션·값·출처. 저장하지 않는다.
     * 정식 서류 파일(Markdown·PDF)은 generated-documents 의 download 를 쓴다.
     */
    @GetMapping("/api/security-requests/{id}/documents")
    public ResponseEntity<DocumentService.DocumentData> documentData(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @RequestParam(required = false) Long submissionId) {
        DocumentService.DocumentData data = documentService.previewData(member, id, submissionId);
        String fileName = data.kindLabel() + "_" + id + (submissionId == null ? "" : "_s" + submissionId) + ".json";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
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
