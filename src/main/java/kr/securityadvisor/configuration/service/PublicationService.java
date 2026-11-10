package kr.securityadvisor.configuration.service;

import java.util.List;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.domain.Publication;
import kr.securityadvisor.configuration.dto.ConfigurationComparisonResponse;
import kr.securityadvisor.configuration.dto.PublicationResponse;
import kr.securityadvisor.configuration.dto.PublicationSummaryResponse;
import kr.securityadvisor.configuration.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    /** 게시 이력은 관리자에게만 제공하며 가장 최근 버전부터 페이지로 조회한다. */
    public PageResponse<PublicationSummaryResponse> history(MemberPrincipal principal, int page, int size) {
        principal.requireAnyRole(MemberRole.ADMIN);
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("페이지는 0 이상, 크기는 1~100이어야 합니다");
        return PageResponse.from(
                publicationRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "version"))),
                PublicationSummaryResponse::from);
    }

    /** 게시 당시의 직전 게시본과 비교한다. 초안 수정은 이 결과에 영향을 주지 않는다. */
    public ConfigurationComparisonResponse comparison(MemberPrincipal principal, Long id) {
        principal.requireAnyRole(MemberRole.ADMIN);
        Publication target =
                publicationRepository.findById(id).orElseThrow(() -> new NotFoundException("게시본을 찾을 수 없습니다"));
        Integer previous = target.getPreviousVersion();
        if (previous == null)
            return new ConfigurationComparisonResponse(target.getVersion(), target.getVersion(), null, List.of());
        Publication base = publicationRepository
                .findByVersion(previous)
                .orElseThrow(() -> new NotFoundException("기준 게시본을 찾을 수 없습니다"));
        return new ConfigurationComparisonResponse(
                previous, target.getVersion(), null, SchemaComparison.compare(parse(base), parse(target)));
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
