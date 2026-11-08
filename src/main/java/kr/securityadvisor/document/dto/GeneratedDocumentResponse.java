package kr.securityadvisor.document.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.document.domain.DocumentKind;
import kr.securityadvisor.document.domain.GeneratedDocument;

/** 렌더링된 서류 응답(미리보기용 sections + markdown). */
public record GeneratedDocumentResponse(
        Long generatedDocumentId,
        Long securityRequestId,
        Long submissionId,
        DocumentKind kind,
        String kindLabel,
        int version,
        String title,
        List<DocumentSection> sections,
        String markdown,
        String aiSupplement,
        String modelReference,
        int unconfirmedCount,
        String generatedBy,
        Instant generatedAt) {

    public static GeneratedDocumentResponse from(GeneratedDocument d, List<DocumentSection> sections) {
        return new GeneratedDocumentResponse(
                d.getId(),
                d.getSecurityRequestId(),
                d.getSubmissionId(),
                d.getKind(),
                d.getKind().label(),
                d.getVersion(),
                d.getTitle(),
                sections,
                d.getRenderedMarkdown(),
                d.getAiSupplement(),
                d.getModelReference(),
                d.getUnconfirmedCount(),
                d.getGeneratedBy(),
                d.getGeneratedAt());
    }
}
