package kr.securityadvisor.configuration.service;

import java.time.Clock;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.domain.Configuration;
import kr.securityadvisor.configuration.domain.Publication;
import kr.securityadvisor.configuration.dto.ConfigurationDraftCommand;
import kr.securityadvisor.configuration.dto.ConfigurationResponse;
import kr.securityadvisor.configuration.dto.PublicationResponse;
import kr.securityadvisor.configuration.dto.PublishCommand;
import kr.securityadvisor.configuration.repository.ConfigurationRepository;
import kr.securityadvisor.configuration.repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 기관 설정 편집·게시(F-08, 관리자). 초안은 설치당 1건이며 현재 게시본에서 시작한다. 게시하면 새 게시본 버전이 생기고
 * 새 사업 건에만 적용된다(불변식 8). 검토 절차 순서(INFRA 선행)는 1차 범위 고정이라 편집 대상이 아니다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationService publicationService;
    private final FieldSchemaValidator validator;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 현재 초안(없으면 현재 게시본을 복사해 만든다). */
    public ConfigurationResponse current(MemberPrincipal principal) {
        principal.requireAnyRole(MemberRole.ADMIN);
        Publication published = currentPublication();
        Configuration configuration = configurationRepository
                .findFirstByOrderByIdAsc()
                .orElseGet(() -> configurationRepository.save(Configuration.startFrom(
                        published.getVersion(), published.getFieldSchemaJson(), principal.username())));
        return toResponse(configuration, published.getVersion());
    }

    public ConfigurationResponse saveDraft(
            MemberPrincipal principal, Long configurationId, ConfigurationDraftCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        Configuration configuration = require(configurationId);
        configuration.requireRevision(command.expectedRevision());
        validator.validate(command.draft());
        configuration.saveDraft(objectMapper.writeValueAsString(command.draft()), command.note(), principal.username());
        auditRecorder.record(
                principal,
                AuditTargetType.CONFIGURATION,
                String.valueOf(configurationId),
                configuration.getRevision(),
                AuditAction.CONFIGURATION_DRAFT_SAVED,
                null,
                "설정 초안 저장 — 서류 유형 " + command.draft().docTypes().size() + "종, 항목 "
                        + command.draft().fields().size() + "개");
        return toResponse(configuration, currentPublication().getVersion());
    }

    /** 게시: 초안을 검증해 새 게시본 버전으로 만든다. 기존 게시본·사업 건은 바뀌지 않는다. */
    public PublicationResponse publish(MemberPrincipal principal, Long configurationId, PublishCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        Configuration configuration = require(configurationId);
        configuration.requireRevision(command.expectedRevision());
        FieldSchema draft = objectMapper.readValue(configuration.getDraftFieldSchema(), FieldSchema.class);
        validator.validate(draft);
        int newVersion = currentPublication().getVersion() + 1;
        Publication publication = publicationRepository.save(
                Publication.publish(newVersion, configuration.getDraftFieldSchema(), clock.instant()));
        configuration.markPublished(newVersion, principal.username());
        auditRecorder.record(
                principal,
                AuditTargetType.CONFIGURATION,
                String.valueOf(configurationId),
                configuration.getRevision(),
                AuditAction.CONFIGURATION_PUBLISHED,
                null,
                "설정 게시 v" + newVersion + " (게시본 " + publication.getId() + ")");
        return publicationService.get(publication.getId());
    }

    private Configuration require(Long id) {
        return configurationRepository.findById(id).orElseThrow(() -> new NotFoundException("설정 초안을 찾을 수 없습니다: " + id));
    }

    private Publication currentPublication() {
        return publicationRepository
                .findTopByOrderByVersionDesc()
                .orElseThrow(() -> new NotFoundException("게시본이 없습니다"));
    }

    private ConfigurationResponse toResponse(Configuration c, int publishedVersion) {
        return ConfigurationResponse.from(
                c, objectMapper.readValue(c.getDraftFieldSchema(), FieldSchema.class), publishedVersion);
    }
}
