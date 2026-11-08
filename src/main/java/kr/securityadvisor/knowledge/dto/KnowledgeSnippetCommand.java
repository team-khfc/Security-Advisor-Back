package kr.securityadvisor.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 스니펫 생성·수정(관리자). docTypes 가 비면 모든 서류 유형에 적용.
 *
 * @param expectedRevision 수정 시 필수
 */
public record KnowledgeSnippetCommand(
        @NotBlank @Size(max = 200) String sourceTitle,
        @NotBlank @Size(max = 50) String sourceVersion,
        @NotBlank @Size(max = 200) String sourceLocator,
        @NotEmpty List<String> fieldKeys,
        List<String> docTypes,
        @NotBlank @Size(max = 4000) String body,
        Boolean active,
        Long expectedRevision) {}
