package kr.securityadvisor.llm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;

/** AI 요약 작업(F-02). 요약은 AI 제안이라 완료 상태 이름이 "확인 필요"다(원칙 1). */
@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_job_id")
    private Long id;

    @Column(name = "business_case_id", nullable = false)
    private Long businessCaseId;

    @Column(name = "uploaded_file_id", nullable = false)
    private Long uploadedFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "model_reference", length = 200)
    private String modelReference;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "requested_by", nullable = false, length = 50)
    private String requestedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(nullable = false)
    private long revision;

    protected AnalysisJob() {}

    private AnalysisJob(Long businessCaseId, Long uploadedFileId, String requestedBy) {
        this.businessCaseId = businessCaseId;
        this.uploadedFileId = uploadedFileId;
        this.requestedBy = requestedBy;
        this.status = AnalysisStatus.QUEUED;
    }

    public static AnalysisJob queue(Long businessCaseId, Long uploadedFileId, String requestedBy) {
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new DomainRuleViolationException("분석을 요청한 사용자가 필요합니다");
        }
        return new AnalysisJob(businessCaseId, uploadedFileId, requestedBy);
    }

    public void start(Instant now) {
        if (status != AnalysisStatus.QUEUED) {
            throw new DomainRuleViolationException("대기 중인 작업만 시작할 수 있습니다 (현재 " + status.label() + ")");
        }
        this.status = AnalysisStatus.RUNNING;
        this.startedAt = now;
    }

    /** 요약 완료 → 확인 필요(미확정). */
    public void finish(String summary, String modelReference, Instant now) {
        if (status != AnalysisStatus.RUNNING) {
            throw new DomainRuleViolationException("분석 중인 작업만 완료할 수 있습니다");
        }
        if (summary == null || summary.isBlank()) {
            throw new DomainRuleViolationException("요약이 비어 있습니다");
        }
        this.status = AnalysisStatus.NEEDS_REVIEW;
        this.summary = summary.strip();
        this.modelReference = modelReference;
        this.finishedAt = now;
    }

    public void fail(String message, Instant now) {
        this.status = AnalysisStatus.FAILED;
        this.errorMessage = message == null ? "알 수 없는 오류" : message.substring(0, Math.min(message.length(), 500));
        this.finishedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getBusinessCaseId() {
        return businessCaseId;
    }

    public Long getUploadedFileId() {
        return uploadedFileId;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getModelReference() {
        return modelReference;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public long getRevision() {
        return revision;
    }
}
