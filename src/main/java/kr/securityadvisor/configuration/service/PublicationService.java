package kr.securityadvisor.configuration.service;

import lombok.RequiredArgsConstructor;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.configuration.domain.Publication;
import kr.securityadvisor.configuration.dto.PublicationResponse;
import kr.securityadvisor.configuration.repository.PublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 게시본 조회. 다른 컨텍스트(request)는 이 서비스의 공개 메서드로만 게시본을 읽는다(컨텍스트 간 참조는 ID + 공개 서비스).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 현재 게시본(가장 최근 버전).
     *
     * @throws NotFoundException 게시본이 하나도 없을 때(seed 누락)
     */
    public PublicationResponse getCurrent() {
        return toResponse(currentPublication());
    }

    /** 새 사업 건에 고정할 현재 게시본 ID. */
    public Long currentPublicationId() {
        return currentPublication().getId();
    }

    /**
     * 게시본 하나(버전·스키마 포함).
     *
     * @throws NotFoundException 해당 게시본이 없을 때
     */
    public PublicationResponse get(Long publicationId) {
        return toResponse(publicationRepository
                .findById(publicationId)
                .orElseThrow(() -> new NotFoundException("게시본을 찾을 수 없습니다: " + publicationId)));
    }

    /**
     * 게시본의 필드 스키마.
     *
     * @throws NotFoundException 해당 게시본이 없을 때
     */
    public FieldSchema getFieldSchema(Long publicationId) {
        Publication publication = publicationRepository
                .findById(publicationId)
                .orElseThrow(() -> new NotFoundException("게시본을 찾을 수 없습니다: " + publicationId));
        return parse(publication);
    }

    private Publication currentPublication() {
        return publicationRepository
                .findTopByOrderByVersionDesc()
                .orElseThrow(() -> new NotFoundException("게시된 기관 설정이 없습니다"));
    }

    private PublicationResponse toResponse(Publication publication) {
        return new PublicationResponse(
                publication.getId(), publication.getVersion(), publication.getPublishedAt(), parse(publication));
    }

    private FieldSchema parse(Publication publication) {
        return objectMapper.readValue(publication.getFieldSchemaJson(), FieldSchema.class);
    }
}
