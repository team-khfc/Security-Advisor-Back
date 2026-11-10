package kr.securityadvisor.knowledge.repository;

import java.util.List;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** 게시본 안의 공개 조각만 전문검색한다. 비공개 데이터는 검색 결과에 포함하지 않는다. */
@Repository
@RequiredArgsConstructor
public class PublishedKnowledgeSearch {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private static final String SOURCE =
            " FROM knowledge_publications p, jsonb_array_elements(p.snapshot->'snippets') s WHERE p.knowledge_publication_id = :id AND s->>'active' = 'true' ";

    public List<KnowledgeSnippetResponse> forField(Long id, String fieldKey, String docType, String query, int limit) {
        return rows(jdbc.sql(
                        "SELECT s::text " + SOURCE
                                + " AND (s->'fieldKeys') @> jsonb_build_array(CAST(:fieldKey AS text))"
                                + " AND (:docType = '' OR s->'docTypes' = '[]'::jsonb OR (s->'docTypes') @> jsonb_build_array(CAST(:docType AS text)))"
                                + " ORDER BY ts_rank(to_tsvector('simple', s->>'body'), plainto_tsquery('simple', :query)) DESC, (s->>'knowledgeSnippetId')::bigint LIMIT :limit")
                .param("id", id)
                .param("fieldKey", fieldKey)
                .param("docType", docType)
                .param("query", query)
                .param("limit", limit));
    }

    public List<KnowledgeSnippetResponse> search(Long id, String query, int limit) {
        return rows(jdbc.sql(
                        "SELECT s::text " + SOURCE
                                + " AND to_tsvector('simple', s->>'body') @@ to_tsquery('simple', :query)"
                                + " ORDER BY ts_rank(to_tsvector('simple', s->>'body'), to_tsquery('simple', :query)) DESC, (s->>'knowledgeSnippetId')::bigint LIMIT :limit")
                .param("id", id)
                .param("query", query)
                .param("limit", limit));
    }

    private List<KnowledgeSnippetResponse> rows(JdbcClient.StatementSpec statement) {
        return statement.query(String.class).list().stream()
                .map(value -> mapper.readValue(value, KnowledgeSnippetResponse.class))
                .toList();
    }
}
