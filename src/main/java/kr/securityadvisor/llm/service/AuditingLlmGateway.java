package kr.securityadvisor.llm.service;

import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.llm.domain.LlmPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모든 AI 호출을 감싸 감사 이력을 남기는 진입점(F-14). 성공·실패 모두 별도 트랜잭션(REQUIRES_NEW)으로 기록해 호출 실패로 업무
 * 트랜잭션이 롤백되어도 기록은 남는다. 프롬프트·응답 원문은 기록하지 않는다(불변식 25).
 */
@Service
@RequiredArgsConstructor
public class AuditingLlmGateway implements LlmGateway {

    private final LlmBackend backend;
    private final LlmCallAuditWriter auditWriter;

    @Override
    public LlmResult complete(LlmPurpose purpose, LlmPrompt prompt, LlmCallContext context) {
        long started = System.nanoTime();
        try {
            LlmResult result = backend.complete(purpose, prompt);
            auditWriter.write(
                    context,
                    AuditAction.LLM_CALLED,
                    purpose + " 호출 성공 — 구성 " + result.modelReference()
                            + (result.configurationId() == null ? "" : " (ID " + result.configurationId() + ")")
                            + ", 소요 " + elapsedMillis(started) + "ms, 응답 "
                            + result.text().length() + "자");
            return result;
        } catch (RuntimeException e) {
            auditWriter.write(
                    context,
                    AuditAction.LLM_CALL_FAILED,
                    purpose + " 호출 실패 — " + e.getClass().getSimpleName() + ", 소요 " + elapsedMillis(started) + "ms");
            throw e;
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** 별도 트랜잭션 기록 — 자기 호출로는 프록시를 타지 않으므로 분리한 빈. */
    @Service
    @RequiredArgsConstructor
    public static class LlmCallAuditWriter {

        private final AuditRecorder auditRecorder;

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void write(LlmCallContext context, AuditAction action, String summary) {
            AuditTargetType targetType = context == null || context.targetType() == null
                    ? AuditTargetType.LLM_CONFIGURATION
                    : context.targetType();
            String targetId = context == null || context.targetId() == null ? "-" : context.targetId();
            auditRecorder.record(
                    context == null || context.actor() == null
                            ? null
                            : context.actor().username(),
                    context == null || context.actor() == null
                            ? null
                            : context.actor().role(),
                    targetType,
                    targetId,
                    null,
                    action,
                    null,
                    summary);
        }
    }
}
