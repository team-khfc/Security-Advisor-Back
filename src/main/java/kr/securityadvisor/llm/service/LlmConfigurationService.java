package kr.securityadvisor.llm.service;

import java.util.Arrays;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.dto.PageResponse;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmConfigurationHistory;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.dto.LlmComparisonResponse;
import kr.securityadvisor.llm.dto.LlmConfigurationCommand;
import kr.securityadvisor.llm.dto.LlmConfigurationResponse;
import kr.securityadvisor.llm.dto.LlmConfigurationValues;
import kr.securityadvisor.llm.dto.LlmHistoryResponse;
import kr.securityadvisor.llm.dto.LlmManagementResponse;
import kr.securityadvisor.llm.dto.LlmRestoreCommand;
import kr.securityadvisor.llm.repository.LlmConfigurationHistoryRepository;
import kr.securityadvisor.llm.repository.LlmConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 관리자 모델 구성: 비교한 revision으로 적용하고 매 변경의 불변 사본을 보존한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LlmConfigurationService {
    private final LlmConfigurationRepository repository;
    private final LlmConfigurationHistoryRepository histories;
    private final LlmConfigurationValuesService values;
    private final LlmCredentialService credentials;
    private final LlmProperties properties;
    private final LlmConfigurationSelector selector;
    private final ObjectMapper mapper;
    private final AuditRecorder audit;

    /** 관리자에게만 키 값 없이 구성을 제공한다. */
    public List<LlmConfigurationResponse> list(MemberPrincipal principal) {
        principal.requireAnyRole(MemberRole.ADMIN);
        return repository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(this::response)
                .toList();
    }

    /** 현재 관리자 권한을 재확인한 뒤 저장된 자원을 조회한다. */
    public LlmConfigurationResponse get(MemberPrincipal principal, Long id) {
        principal.requireAnyRole(MemberRole.ADMIN);
        return response(require(id, false));
    }

    /** 시연 응답과 실제 용도별 선택 결과를 구분한다. */
    public LlmManagementResponse management(MemberPrincipal principal) {
        principal.requireAnyRole(MemberRole.ADMIN);
        var keys = credentials.allowedReferences().stream()
                .map(k -> new LlmManagementResponse.KeyReference(k, credentials.configured(k)))
                .toList();
        var routes = Arrays.stream(LlmPurpose.values())
                .map(p -> {
                    if (properties.isFake())
                        return new LlmManagementResponse.Route(p, null, "fake/offline", "SIMULATED");
                    try {
                        var c = selector.select(p).configuration();
                        return new LlmManagementResponse.Route(p, c.getId(), c.reference(), "READY");
                    } catch (DomainRuleViolationException
                            | kr.securityadvisor.common.error.ServiceUnavailableException e) {
                        return new LlmManagementResponse.Route(p, null, null, "UNAVAILABLE");
                    }
                })
                .toList();
        return new LlmManagementResponse(
                properties.isFake() ? "FAKE" : "LIVE",
                properties.allowedHostSet().stream().sorted().toList(),
                keys,
                routes);
    }

    /** 저장하지 않고 같은 검증 규칙으로 변경 전후를 비교한다. */
    public LlmComparisonResponse compare(MemberPrincipal principal, Long id, LlmConfigurationCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        LlmConfiguration current = id == null ? null : require(id, false);
        if (current != null) revision(current, command.expectedRevision());
        var candidate = values.values(values.candidate(command, current));
        var before = current == null ? null : values.values(current);
        return new LlmComparisonResponse(
                current == null ? null : current.getRevision(), before, candidate, values.changes(before, candidate));
    }

    /** 명시한 활성 상태를 보존하고 최초 이력을 함께 기록한다. */
    @Transactional
    public LlmConfigurationResponse create(MemberPrincipal principal, LlmConfigurationCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        var candidate = values.candidate(command, null);
        var created = repository.saveAndFlush(candidate);
        record(principal, created, "CREATED", command.reason(), null, AuditAction.LLM_CONFIGURATION_CREATED);
        return response(created);
    }

    /** 기준 버전이 일치할 때만 구성과 이력을 함께 변경한다. */
    @Transactional
    public LlmConfigurationResponse update(MemberPrincipal principal, Long id, LlmConfigurationCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        return change(principal, require(id, true), command, null);
    }

    /** 과거 값을 현재 정책으로 검증해 새 이력으로 복원한다. */
    @Transactional
    public LlmConfigurationResponse restore(MemberPrincipal principal, Long id, LlmRestoreCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        var current = require(id, true);
        revision(current, command.expectedRevision());
        var history = historyEntity(id, command.historyId());
        var snapshot = mapper.readValue(history.getSnapshot(), LlmConfigurationValues.class);
        return change(
                principal, current, snapshot.command(command.expectedRevision(), command.reason()), history.getId());
    }

    /** 구성별 불변 이력을 페이지 단위로 제공한다. */
    public PageResponse<LlmHistoryResponse> history(MemberPrincipal principal, Long id, int page, int size) {
        principal.requireAnyRole(MemberRole.ADMIN);
        require(id, false);
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("페이지는 0 이상, 크기는 1~100이어야 합니다");
        return PageResponse.from(
                histories.findByConfigurationIdOrderByIdDesc(id, PageRequest.of(page, size)), this::historyResponse);
    }

    private LlmConfigurationResponse change(
            MemberPrincipal principal, LlmConfiguration current, LlmConfigurationCommand command, Long restoredFrom) {
        revision(current, command.expectedRevision());
        var candidate = values.candidate(command, current);
        if (values.changes(values.values(current), values.values(candidate)).isEmpty())
            throw new DomainRuleViolationException("현재 구성과 다른 내용이 없습니다");
        values.apply(current, candidate);
        repository.flush();
        record(
                principal,
                current,
                restoredFrom == null ? "UPDATED" : "RESTORED",
                command.reason(),
                restoredFrom,
                restoredFrom == null ? AuditAction.LLM_CONFIGURATION_UPDATED : AuditAction.LLM_CONFIGURATION_RESTORED);
        return response(current);
    }

    /** 연결 시험 준비도 기존 구성 버전을 확인하되 외부 호출 동안 DB 잠금은 보유하지 않는다. */
    public LlmConfiguration candidate(
            MemberPrincipal principal, Long id, Long expectedRevision, LlmConfigurationCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        var current = id == null ? null : require(id, false);
        if (current != null) revision(current, expectedRevision);
        return values.candidate(command, current);
    }

    private void record(
            MemberPrincipal principal,
            LlmConfiguration c,
            String action,
            String reason,
            Long restoredFrom,
            AuditAction auditAction) {
        var h = histories.save(LlmConfigurationHistory.record(
                c.getId(),
                c.getRevision(),
                mapper.writeValueAsString(values.values(c)),
                action,
                principal.username(),
                reason,
                restoredFrom));
        audit.record(
                principal,
                AuditTargetType.LLM_CONFIGURATION,
                c.getId().toString(),
                c.getRevision(),
                auditAction,
                null,
                "모델 구성 " + action + " 이력 #" + h.getId());
    }

    private LlmConfigurationHistory historyEntity(Long id, Long historyId) {
        return histories
                .findById(historyId)
                .filter(h -> h.getConfigurationId().equals(id))
                .orElseThrow(() -> new NotFoundException("이 구성의 변경 이력을 찾을 수 없습니다"));
    }

    private LlmHistoryResponse historyResponse(LlmConfigurationHistory h) {
        return new LlmHistoryResponse(
                h.getId(),
                h.getConfigurationId(),
                h.getConfigurationRevision(),
                h.getAction(),
                h.getChangedBy(),
                h.getCreatedAt(),
                h.getReason(),
                h.getRestoredFromId(),
                mapper.readValue(h.getSnapshot(), LlmConfigurationValues.class));
    }

    private LlmConfigurationResponse response(LlmConfiguration c) {
        return LlmConfigurationResponse.from(c, values.values(c).purposes(), credentials.configured(c.getApiKeyEnv()));
    }

    private LlmConfiguration require(Long id, boolean lock) {
        return (lock ? repository.lockById(id) : repository.findById(id))
                .orElseThrow(() -> new NotFoundException("모델 구성을 찾을 수 없습니다"));
    }

    private static void revision(LlmConfiguration c, Long expected) {
        if (expected == null) throw new DomainRuleViolationException("수정 기준 버전이 필요합니다");
        c.requireRevision(expected);
    }
}
