package kr.securityadvisor.knowledge.repository;

import java.util.List;
import kr.securityadvisor.knowledge.domain.KnowledgeSnippet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 스니펫 저장소. 검색은 Postgres 네이티브(jsonb 포함 + 전문검색). */
public interface KnowledgeSnippetRepository extends JpaRepository<KnowledgeSnippet, Long> {

    List<KnowledgeSnippet> findAllByOrderByIdAsc();

    /** 항목 키로 1차 필터(사람 태깅), 서류 유형 일치, 활성만. 질의 텍스트가 있으면 FTS 순위, 없으면 ID 순. */
    @Query(value = """
            SELECT s.* FROM knowledge_snippets s
            WHERE s.active
              AND s.field_keys @> jsonb_build_array(CAST(:fieldKey AS text))
              AND (s.doc_types @> jsonb_build_array(CAST(:docType AS text)) OR s.doc_types = '[]'::jsonb)
            ORDER BY CASE WHEN :query = '' THEN 0
                          ELSE ts_rank(s.search_vector, plainto_tsquery('simple', CAST(:query AS text))) END DESC,
                     s.knowledge_snippet_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<KnowledgeSnippet> findForField(
            @Param("fieldKey") String fieldKey,
            @Param("docType") String docType,
            @Param("query") String query,
            @Param("limit") int limit);

    /** 자유 질의(담당자 AI 질의용): 전문검색만. 접두 매칭으로 조사 붙은 한국어 토큰을 어느 정도 흡수한다. */
    @Query(value = """
            SELECT s.* FROM knowledge_snippets s
            WHERE s.active AND s.search_vector @@ to_tsquery('simple', CAST(:tsquery AS text))
            ORDER BY ts_rank(s.search_vector, to_tsquery('simple', CAST(:tsquery AS text))) DESC, s.knowledge_snippet_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<KnowledgeSnippet> search(@Param("tsquery") String tsquery, @Param("limit") int limit);
}
