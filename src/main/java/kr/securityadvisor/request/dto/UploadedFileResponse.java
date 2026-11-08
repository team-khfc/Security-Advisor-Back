package kr.securityadvisor.request.dto;

import java.time.Instant;
import kr.securityadvisor.request.domain.UploadedFile;

/** 업로드 문서 응답(저장 파일명은 노출하지 않는다). */
public record UploadedFileResponse(
        Long uploadedFileId,
        Long businessCaseId,
        String originalName,
        String contentType,
        long sizeBytes,
        String uploadedBy,
        Instant uploadedAt) {

    public static UploadedFileResponse from(UploadedFile f) {
        return new UploadedFileResponse(
                f.getId(),
                f.getBusinessCaseId(),
                f.getOriginalName(),
                f.getContentType(),
                f.getSizeBytes(),
                f.getUploadedBy(),
                f.getUploadedAt());
    }
}
