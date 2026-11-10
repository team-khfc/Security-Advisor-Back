package kr.securityadvisor.llm.service;

import java.util.concurrent.Semaphore;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.error.TooManyRequestsException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.llm.domain.LlmConnectionTest;
import kr.securityadvisor.llm.dto.LlmConfigurationValues;
import kr.securityadvisor.llm.dto.LlmConnectionTestCommand;
import kr.securityadvisor.llm.dto.LlmConnectionTestResponse;
import kr.securityadvisor.llm.repository.LlmConfigurationRepository;
import kr.securityadvisor.llm.repository.LlmConnectionTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 외부 호출 동안 DB 트랜잭션을 열지 않고 시험 결과만 짧게 기록한다. */
@Service
@RequiredArgsConstructor
public class LlmConnectionTestService {
    private final LlmConfigurationService configurations;
    private final LlmConfigurationValuesService values;
    private final LlmCredentialService credentials;
    private final LlmConnectionProbe probe;
    private final LlmConnectionTestRepository tests;
    private final LlmConfigurationRepository repository;
    private final TransactionTemplate transactions;
    private final AuditRecorder audit;
    private final ObjectMapper mapper;
    private final Semaphore slots = new Semaphore(2);

    /** 고정 합성 입력만 시험하고 설정을 변경하지 않는다. */
    public LlmConnectionTestResponse test(MemberPrincipal principal, LlmConnectionTestCommand command) {
        principal.requireAnyRole(MemberRole.ADMIN);
        var candidate = configurations.candidate(
                principal, command.configurationId(), command.expectedRevision(), command.configuration());
        if (!slots.tryAcquire()) throw new TooManyRequestsException("연결 시험이 진행 중입니다. 잠시 후 다시 시도해 주세요");
        try {
            long started = System.nanoTime();
            var outcome = credentials.configured(candidate.getApiKeyEnv())
                    ? probe.test(candidate, credentials.key(candidate.getApiKeyEnv()))
                    : LlmConnectionProbe.Outcome.KEY_NOT_CONFIGURED;
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            var result = transactions.execute(status -> {
                var saved = tests.saveAndFlush(LlmConnectionTest.record(
                        command.configurationId(),
                        command.expectedRevision(),
                        mapper.writeValueAsString(values.values(candidate)),
                        outcome.name(),
                        elapsed,
                        principal.username()));
                audit.record(
                        principal,
                        AuditTargetType.LLM_CONFIGURATION,
                        command.configurationId() == null
                                ? "-"
                                : command.configurationId().toString(),
                        command.expectedRevision(),
                        AuditAction.LLM_CONNECTION_TESTED,
                        null,
                        "연결 시험 #" + saved.getId() + " " + outcome.name() + " " + elapsed + "ms");
                return response(saved);
            });
            return result;
        } finally {
            slots.release();
        }
    }

    /** 현재 관리자 권한을 재확인한 뒤 저장된 자원을 조회한다. */
    public LlmConnectionTestResponse get(MemberPrincipal principal, Long id) {
        principal.requireAnyRole(MemberRole.ADMIN);
        return response(tests.findById(id).orElseThrow(() -> new NotFoundException("연결 시험 기록이 없습니다")));
    }

    private LlmConnectionTestResponse response(LlmConnectionTest test) {
        var outcome = LlmConnectionProbe.Outcome.valueOf(test.getOutcome());
        boolean stale = test.getConfigurationId() != null
                && repository
                        .findById(test.getConfigurationId())
                        .map(c -> !Long.valueOf(c.getRevision()).equals(test.getConfigurationRevision()))
                        .orElse(true);
        return new LlmConnectionTestResponse(
                test.getId(),
                test.getConfigurationId(),
                test.getConfigurationRevision(),
                outcome == LlmConnectionProbe.Outcome.CONNECTED ? "SUCCEEDED" : "FAILED",
                outcome.name(),
                outcome.message(),
                test.getElapsedMillis(),
                test.getCreatedAt(),
                stale,
                mapper.readValue(test.getSnapshot(), LlmConfigurationValues.class));
    }
}
