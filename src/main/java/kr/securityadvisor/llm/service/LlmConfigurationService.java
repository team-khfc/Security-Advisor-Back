package kr.securityadvisor.llm.service;

import java.math.BigDecimal;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.dto.LlmConfigurationCommand;
import kr.securityadvisor.llm.dto.LlmConfigurationResponse;
import kr.securityadvisor.llm.repository.LlmConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LLM 구성 관리(F-10, 관리자). 변경은 감사 이력(값 요약만, 키 없음). */
@Service
@Transactional
@RequiredArgsConstructor
public class LlmConfigurationService {

    private final LlmConfigurationRepository repository;
    private final LlmProperties properties;
    private final Environment environment;
    private final JsonCodec json;
    private final AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public List<LlmConfigurationResponse> list(MemberPrincipal principal) {
        principal.requireAnyRole(MemberRole.ADMIN);
        return repository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public LlmConfigurationResponse create(MemberPrincipal principal, LlmConfigurationCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        List<String> purposes = purposesOf(command.purposes());
        LlmConfiguration configuration = repository.save(LlmConfiguration.create(
                command.name(),
                command.provider(),
                command.baseUrl(),
                command.model(),
                command.apiKeyEnv(),
                json.writeStrings(purposes),
                !purposes.isEmpty(),
                command.temperature() == null ? new BigDecimal("0.20") : command.temperature(),
                command.maxTokens() == null ? 1024 : command.maxTokens(),
                command.timeoutSeconds() == null ? 60 : command.timeoutSeconds(),
                command.priority() == null ? 100 : command.priority(),
                properties.allowedHostSet()));
        auditRecorder.record(
                principal,
                AuditTargetType.LLM_CONFIGURATION,
                String.valueOf(configuration.getId()),
                configuration.getRevision(),
                AuditAction.LLM_CONFIGURATION_CREATED,
                null,
                "LLM 구성 생성 " + configuration.reference() + " host="
                        + LlmConfiguration.hostOf(configuration.getBaseUrl()));
        return toResponse(configuration);
    }

    public LlmConfigurationResponse update(MemberPrincipal principal, Long id, LlmConfigurationCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        LlmConfiguration configuration =
                repository.findById(id).orElseThrow(() -> new NotFoundException("LLM 구성을 찾을 수 없습니다: " + id));
        if (command.expectedRevision() == null) {
            throw new DomainRuleViolationException("수정에는 expectedRevision 이 필요합니다");
        }
        configuration.requireRevision(command.expectedRevision());
        List<String> purposes = purposesOf(command.purposes());
        configuration.update(
                command.name(),
                command.provider(),
                command.baseUrl(),
                command.model(),
                command.apiKeyEnv(),
                json.writeStrings(purposes),
                !purposes.isEmpty(),
                command.temperature() == null ? configuration.getTemperature() : command.temperature(),
                command.maxTokens() == null ? configuration.getMaxTokens() : command.maxTokens(),
                command.timeoutSeconds() == null ? configuration.getTimeoutSeconds() : command.timeoutSeconds(),
                command.priority() == null ? configuration.getPriority() : command.priority(),
                command.active() == null || command.active(),
                properties.allowedHostSet());
        auditRecorder.record(
                principal,
                AuditTargetType.LLM_CONFIGURATION,
                String.valueOf(id),
                configuration.getRevision(),
                AuditAction.LLM_CONFIGURATION_UPDATED,
                null,
                "LLM 구성 수정 " + configuration.reference() + " active=" + configuration.isActive());
        return toResponse(configuration);
    }

    private LlmConfigurationResponse toResponse(LlmConfiguration c) {
        String key = environment.getProperty(c.getApiKeyEnv());
        return LlmConfigurationResponse.from(c, json.readStrings(c.getPurposes()), key != null && !key.isBlank());
    }

    private static List<String> purposesOf(List<LlmPurpose> purposes) {
        return purposes == null
                ? List.of()
                : purposes.stream().map(LlmPurpose::name).distinct().toList();
    }
}
