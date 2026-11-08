package kr.securityadvisor.knowledge.dto;

import java.util.List;
import kr.securityadvisor.knowledge.domain.KnowledgeSnippet;

/** 스니펫 응답. */
public record KnowledgeSnippetResponse(
        Long knowledgeSnippetId,
        String sourceTitle,
        String sourceVersion,
        String sourceLocator,
        String citation,
        List<String> fieldKeys,
        List<String> docTypes,
        String body,
        boolean active,
        long revision) {

    public static KnowledgeSnippetResponse from(KnowledgeSnippet s, List<String> fieldKeys, List<String> docTypes) {
        return new KnowledgeSnippetResponse(
                s.getId(),
                s.getSourceTitle(),
                s.getSourceVersion(),
                s.getSourceLocator(),
                s.citation(),
                fieldKeys,
                docTypes,
                s.getBody(),
                s.isActive(),
                s.getRevision());
    }
}
