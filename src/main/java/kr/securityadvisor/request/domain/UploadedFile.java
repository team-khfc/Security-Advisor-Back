package kr.securityadvisor.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;

/** 사업 건 업로드 문서(F-02). 저장 파일명은 서버가 만든 UUID 이고 원래 이름은 표시용이다. 생성 후 불변. */
@Entity
@Table(name = "uploaded_files")
public class UploadedFile extends BaseTimeEntity {

    /** 허용 형식. 구 HWP(바이너리)는 제외 — HWPX 로 변환해 올리도록 안내한다({@link FileContentCheck#HWP_GUIDE}). */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "hwpx", "txt", "md");

    public static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uploaded_file_id")
    private Long id;

    @Column(name = "business_case_id", nullable = false)
    private Long businessCaseId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 64)
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", nullable = false, length = 50)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected UploadedFile() {}

    private UploadedFile(
            Long businessCaseId,
            String originalName,
            String storedName,
            String contentType,
            long sizeBytes,
            String uploadedBy,
            Instant uploadedAt) {
        this.businessCaseId = businessCaseId;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    /** 이름·크기·형식 검사 후 생성. 저장 파일명은 확장자만 붙인 UUID. */
    public static UploadedFile register(
            Long businessCaseId,
            String originalName,
            String contentType,
            long sizeBytes,
            String uploadedBy,
            Instant now) {
        if (originalName == null || originalName.isBlank()) {
            throw new DomainRuleViolationException("파일 이름이 없습니다");
        }
        String cleaned = originalName.replace("\\", "/");
        cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1).strip();
        if (cleaned.isEmpty() || cleaned.length() > 255) {
            throw new DomainRuleViolationException("파일 이름이 올바르지 않습니다");
        }
        String extension = extensionOf(cleaned);
        if ("hwp".equals(extension)) {
            throw new DomainRuleViolationException(FileContentCheck.HWP_GUIDE);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new DomainRuleViolationException("허용되지 않는 파일 형식입니다(" + extension + "). 허용: " + ALLOWED_EXTENSIONS);
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new DomainRuleViolationException("파일 크기는 1바이트 이상 " + (MAX_SIZE_BYTES / 1024 / 1024) + "MB 이하여야 합니다");
        }
        if (uploadedBy == null || uploadedBy.isBlank()) {
            throw new DomainRuleViolationException("업로드한 사용자가 필요합니다");
        }
        String storedName = UUID.randomUUID() + "." + extension;
        return new UploadedFile(
                businessCaseId,
                cleaned,
                storedName,
                contentType == null ? "application/octet-stream" : contentType,
                sizeBytes,
                uploadedBy,
                now);
    }

    public static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    public String extension() {
        return extensionOf(storedName);
    }

    public Long getId() {
        return id;
    }

    public Long getBusinessCaseId() {
        return businessCaseId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
