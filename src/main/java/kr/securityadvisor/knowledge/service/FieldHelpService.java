package kr.securityadvisor.knowledge.service;

import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.knowledge.dto.FieldHelpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 항목 도움말: 게시본의 항목 정의(왜 묻는지) + 그 항목에 태깅된 공개자료 스니펫. 모델을 부르지 않으므로 누구나 빠르게 조회한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FieldHelpService {

    private final PublicationService publicationService;
    private final KnowledgeSearch knowledgeSearch;
    private final KnowledgePublicationService knowledgePublications;

    /**
     * @param publicationId 게시본(없으면 현재 게시본)
     * @param docType 서류 유형(없으면 유형 무관 스니펫까지)
     * @param queryText 사용자 답변 등 순위용 문장(선택)
     * @throws NotFoundException 게시본에 없는 항목(404)
     */
    public FieldHelpResponse help(String fieldKey, Long publicationId, DocTypeCode docType, String queryText) {
        return help(fieldKey, publicationId, docType, queryText, null);
    }

    public FieldHelpResponse help(
            String fieldKey, Long publicationId, DocTypeCode docType, String queryText, Long knowledgePublicationId) {
        Long resolved = publicationId == null ? publicationService.currentPublicationId() : publicationId;
        FieldDefinition field = publicationService
                .getFieldSchema(resolved)
                .find(fieldKey)
                .orElseThrow(() -> new NotFoundException("게시본에 없는 항목입니다: " + fieldKey));
        return new FieldHelpResponse(
                field.key(),
                field.label(),
                field.required(),
                field.askRole() == null ? null : field.askRole().name(),
                field.hint(),
                knowledgeSearch.forField(
                        fieldKey,
                        docType,
                        queryText,
                        knowledgePublicationId == null ? knowledgePublications.currentId() : knowledgePublicationId));
    }
}
