package kr.securityadvisor.knowledge.dto;

import java.util.List;

/** 비공개 원문을 포함하므로 관리자 API에서만 전체 사본을 반환한다. */
public record KnowledgeSnapshot(
        List<KnowledgeSnippetResponse> snippets, List<PrivateContextResponse> privateContexts) {}
