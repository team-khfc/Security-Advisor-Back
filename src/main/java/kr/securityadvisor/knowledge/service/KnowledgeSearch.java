package kr.securityadvisor.knowledge.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.knowledge.domain.KnowledgeSnippet;
import kr.securityadvisor.knowledge.repository.KnowledgeSnippetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 지식 검색(F-09, 1차 범위: 항목 키 태깅 1차 필터 + Postgres 전문검색 순위). 임베딩·벡터 색인은 쓰지 않는다(ADR 0006).
 * 반환하는 조각은 "지식"이며, 기관 "사실"(PrivateContextLookup)과 프롬프트에서 분리된다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class KnowledgeSearch {

    private static final int DEFAULT_LIMIT = 3;

    private final KnowledgeSnippetRepository repository;
    private final JsonCodec json;

    /** 한 항목에 적용되는 지식. docType 이 null 이면 유형 무관. queryText(사용자 답변·요약)가 있으면 그 단어로 순위를 매긴다. */
    public List<SnippetHit> forField(String fieldKey, DocTypeCode docType, String queryText) {
        return repository
                .findForField(
                        fieldKey,
                        docType == null ? "" : docType.code(),
                        queryText == null ? "" : normalize(queryText),
                        DEFAULT_LIMIT)
                .stream()
                .map(this::toHit)
                .toList();
    }

    /** 자유 질의: 단어마다 접두 매칭(한국어 조사 흡수), AND 로 묶고 결과가 없으면 OR 로 완화한다. */
    public List<SnippetHit> search(String queryText, int limit) {
        List<String> terms = Arrays.stream(normalize(queryText).split("\\s+"))
                .filter(t -> t.length() >= 2)
                .limit(8)
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        List<KnowledgeSnippet> hits =
                repository.search(terms.stream().map(t -> t + ":*").collect(Collectors.joining(" & ")), limit);
        if (hits.isEmpty() && terms.size() > 1) {
            hits = repository.search(terms.stream().map(t -> t + ":*").collect(Collectors.joining(" | ")), limit);
        }
        return hits.stream().map(this::toHit).toList();
    }

    SnippetHit toHit(KnowledgeSnippet snippet) {
        return new SnippetHit(
                snippet.getId(), snippet.citation(), snippet.getBody(), json.readStrings(snippet.getFieldKeys()));
    }

    /** tsquery 구문 문자를 제거해 입력이 검색식으로 해석되지 않게 한다. */
    private static String normalize(String text) {
        return text.replaceAll("[&|!():*'\"<>\\\\]", " ").strip();
    }
}
