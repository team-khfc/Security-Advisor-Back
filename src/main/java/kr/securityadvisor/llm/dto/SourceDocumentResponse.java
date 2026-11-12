package kr.securityadvisor.llm.dto;

import java.time.Instant;

/** AI 요약과 구별되는 첨부 원문의 추출문. 줄 번호는 원본 페이지 번호가 아니다. */
public record SourceDocumentResponse(
        Long uploadedFileId,
        String originalName,
        Instant uploadedAt,
        String text,
        int maximumCharacters,
        boolean possiblyTruncated) {}
