package kr.securityadvisor.llm.service;

import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.domain.AnalysisJob;
import kr.securityadvisor.llm.domain.AnalysisStatus;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.dto.AnalysisJobCommand;
import kr.securityadvisor.llm.dto.AnalysisJobResponse;
import kr.securityadvisor.llm.repository.AnalysisJobRepository;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.UploadedFile;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.request.service.UploadedFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 문서 AI 요약(F-02). 요청은 202 로 접수하고 커밋 후 비동기로 처리한다: 텍스트 추출 → SUMMARY 호출 → "확인 필요"(미확정).
 * 원문·프롬프트·응답은 감사 이력에 남기지 않는다(불변식 25).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAnalysisService {

    private static final String SYSTEM = """
            너는 공공기관 보안 서류 작성을 돕는 요약 보조자다. 주어진 문서 텍스트만 근거로 한국어로 요약한다.
            포함할 것: 사업 목적, 대상 시스템·서비스, 외부(인터넷) 연계 여부, 개인정보 등 중요정보 처리 여부, 통신(프로토콜·포트)·기간이 적혀 있으면 그 값.
            문서에 없는 내용은 "문서에 명시되지 않음"이라고 쓴다. 서버명·IP 는 문서에 적힌 그대로만 옮긴다. 300자 안팎, 문단 하나.
            """;

    private final AnalysisJobRepository repository;
    private final RequestAccessPolicy accessPolicy;
    private final UploadedFileService uploadedFileService;
    private final TextExtractor textExtractor;
    private final LlmGateway llmGateway;
    private final AuditRecorder auditRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final kr.securityadvisor.auth.service.MemberDirectory members;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final java.util.concurrent.Semaphore capacity = new java.util.concurrent.Semaphore(2);

    /** 접수 이벤트 — 커밋 후 비동기 처리의 트리거. */
    public record AnalysisQueued(Long analysisJobId) {}

    @Transactional
    public AnalysisJobResponse request(MemberPrincipal principal, Long businessCaseId, AnalysisJobCommand command) {
        BusinessCase businessCase = accessPolicy.ownedCase(principal, businessCaseId);
        UploadedFile file = uploadedFileService.requireFile(businessCaseId, command.uploadedFileId());
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, -file.getId());
        var existing = repository.findFirstByBusinessCaseIdAndUploadedFileIdAndStatusInOrderByIdDesc(
                businessCaseId, file.getId(), List.of(AnalysisStatus.QUEUED, AnalysisStatus.RUNNING));
        if (existing.isPresent()) return AnalysisJobResponse.from(existing.get());
        AnalysisJob job = repository.save(AnalysisJob.queue(businessCase.getId(), file.getId(), principal.username()));
        auditRecorder.record(
                principal,
                AuditTargetType.BUSINESS_CASE,
                String.valueOf(businessCaseId),
                businessCase.getRevision(),
                AuditAction.ANALYSIS_REQUESTED,
                null,
                "문서 AI 요약 요청 (작업 " + job.getId() + ", 파일 " + file.getId() + ")");
        eventPublisher.publishEvent(new AnalysisQueued(job.getId()));
        return AnalysisJobResponse.from(job);
    }

    /** 커밋 뒤 별도 스레드에서 처리. 실패해도 업무 상태는 바뀌지 않고 작업만 FAILED 가 된다. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(AnalysisQueued event) {
        if (!capacity.tryAcquire()) return; // DB 대기 작업은 복구 스케줄러가 다시 가져온다.
        try {
            run(event.analysisJobId());
        } finally {
            capacity.release();
        }
    }

    /** 짧은 트랜잭션으로 실행권을 얻고 외부 AI 호출은 트랜잭션 밖에서 수행한다. */
    void run(Long jobId) {
        AnalysisJob claimed = transactionTemplate.execute(status -> {
            var job = repository.lockById(jobId).orElse(null);
            if (job == null || job.getStatus() != AnalysisStatus.QUEUED) return null;
            if (!members.isActiveWithRole(
                    job.getRequestedBy(), kr.securityadvisor.common.security.MemberRole.APPLICANT)) {
                job.fail("요청자 권한이 변경되어 분석을 중단했습니다.", clock.instant());
                return null;
            }
            job.start(clock.instant());
            return job;
        });
        if (claimed == null) return;
        LlmResult result = null;
        boolean truncated = false;
        try {
            UploadedFile file =
                    uploadedFileService.requireFile(claimed.getBusinessCaseId(), claimed.getUploadedFileId());
            String text;
            try (InputStream in = uploadedFileService.open(file)) {
                text = textExtractor.extractFull(file.extension(), in);
                truncated = text.length() > TextExtractor.MAX_CHARS;
                if (truncated) text = text.substring(0, TextExtractor.MAX_CHARS);
            }
            if (text.isBlank()) throw new IllegalArgumentException("empty document");
            result = llmGateway.complete(
                    LlmPurpose.SUMMARY,
                    LlmPrompt.of(SYSTEM, List.of(), List.of(), "문서 파일명: " + file.getOriginalName() + "\n\n" + text),
                    new LlmCallContext(
                            null, AuditTargetType.BUSINESS_CASE, String.valueOf(claimed.getBusinessCaseId())));
        } catch (Exception e) {
            log.warn("문서 분석 실패 작업 {} ({})", jobId, e.getClass().getSimpleName());
        }
        final LlmResult completed = result;
        final boolean partial = truncated;
        transactionTemplate.executeWithoutResult(status -> {
            var job = repository.lockById(jobId).orElseThrow();
            if (job.getStatus() != AnalysisStatus.RUNNING || job.getAttempts() != claimed.getAttempts()) return;
            if (!members.isActiveWithRole(
                    job.getRequestedBy(), kr.securityadvisor.common.security.MemberRole.APPLICANT)) {
                job.fail("요청자 권한이 변경되어 결과를 저장하지 않았습니다.", clock.instant());
            } else if (completed == null) {
                job.fail("문서 분석에 실패했습니다. 원문을 확인하거나 다시 요청해 주세요.", clock.instant());
            } else {
                job.finish(
                        (partial ? "[앞 20,000자 요약 · 전체 문서는 원문 확인과 구간별 검사 필요]\n" : "") + completed.text(),
                        completed.modelReference(),
                        clock.instant());
            }
            record(
                    job,
                    job.getStatus() == AnalysisStatus.NEEDS_REVIEW
                            ? AuditAction.ANALYSIS_FINISHED
                            : AuditAction.ANALYSIS_FAILED,
                    job.getStatus().name());
        });
    }

    /** 재시작으로 놓친 대기 작업과 15분 이상 중단된 실행을 복구한다. */
    public List<Long> recoverable() {
        return transactionTemplate.execute(status -> {
            var now = clock.instant();
            repository
                    .findTop20ByStatusAndStartedAtBeforeOrderByIdAsc(AnalysisStatus.RUNNING, now.minusSeconds(900))
                    .forEach(candidate -> repository.lockById(candidate.getId()).ifPresent(job -> job.recover(now)));
            return repository.findTop20ByStatusOrderByIdAsc(AnalysisStatus.QUEUED).stream()
                    .map(AnalysisJob::getId)
                    .toList();
        });
    }

    @Transactional(readOnly = true)
    public AnalysisJobResponse get(MemberPrincipal principal, Long jobId) {
        AnalysisJob job =
                repository.findById(jobId).orElseThrow(() -> new NotFoundException("분석 작업을 찾을 수 없습니다: " + jobId));
        accessPolicy.viewableCase(principal, job.getBusinessCaseId());
        return AnalysisJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<AnalysisJobResponse> listForCase(MemberPrincipal principal, Long businessCaseId) {
        accessPolicy.viewableCase(principal, businessCaseId);
        return repository.findByBusinessCaseIdOrderByIdDesc(businessCaseId).stream()
                .map(AnalysisJobResponse::from)
                .toList();
    }

    /** AI 제안·질의 컨텍스트용: 확인 필요 상태(완료)인 요약 본문(최신순). */
    @Transactional(readOnly = true)
    public List<String> summariesOf(Long businessCaseId) {
        return repository
                .findByBusinessCaseIdAndStatusOrderByIdDesc(businessCaseId, AnalysisStatus.NEEDS_REVIEW)
                .stream()
                .map(AnalysisJob::getSummary)
                .toList();
    }

    private void record(AnalysisJob job, AuditAction action, String detail) {
        auditRecorder.record(
                null,
                null,
                AuditTargetType.BUSINESS_CASE,
                String.valueOf(job.getBusinessCaseId()),
                null,
                action,
                null,
                "문서 AI 요약 " + job.getStatus().label() + " (작업 " + job.getId() + ", " + detail + ")");
    }
}
