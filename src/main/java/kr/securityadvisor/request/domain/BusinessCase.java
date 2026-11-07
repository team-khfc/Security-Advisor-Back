package kr.securityadvisor.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.securityadvisor.common.entity.BaseAuditEntity;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;

/**
 * 사업 건 애그리거트 루트. 공통 정보(scope=CASE 답변·업로드 문서)를 한 번만 가지며 서류별 신청 N건을 거느린다.
 *
 * <p>진행 상태는 저장하지 않는다 — 하위 신청에서 계산한다(불변식 15). 신청은 별개 애그리거트이며 {@code businessCaseId} 로만 참조한다.
 * 생성 시점의 게시본을 고정해 질문 스키마가 바뀌어도 이 사업 건에는 영향이 없다(불변식 8).
 */
@Entity
@Table(name = "business_cases")
public class BusinessCase extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "business_case_id")
    private Long id;

    @Column(name = "applicant_username", nullable = false, length = 50)
    private String applicantUsername;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(length = 100)
    private String department;

    @Column(name = "publication_id", nullable = false)
    private Long publicationId;

    @Version
    @Column(nullable = false)
    private long revision;

    /** JPA 전용 기본 생성자. */
    protected BusinessCase() {}

    private BusinessCase(
            String applicantUsername, String title, String summary, String department, Long publicationId) {
        this.applicantUsername = applicantUsername;
        this.title = title;
        this.summary = summary;
        this.department = department;
        this.publicationId = publicationId;
    }

    /**
     * 사업 건을 만든다. 서류 유형 선택(1개 이상)은 호출한 서비스가 {@link SecurityRequest#create} 로 이어서 한다(불변식 14).
     *
     * @throws DomainRuleViolationException 사업명이 없을 때
     */
    public static BusinessCase create(
            String applicantUsername, String title, String summary, String department, Long publicationId) {
        if (title == null || title.isBlank()) {
            throw new DomainRuleViolationException("사업명은 필수입니다");
        }
        return new BusinessCase(applicantUsername, title.strip(), summary, department, publicationId);
    }

    public Long getId() {
        return id;
    }

    public String getApplicantUsername() {
        return applicantUsername;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getDepartment() {
        return department;
    }

    public Long getPublicationId() {
        return publicationId;
    }

    public long getRevision() {
        return revision;
    }

    public boolean isOwnedBy(String username) {
        return applicantUsername.equals(username);
    }

    /**
     * @throws ConflictException expectedRevision 이 현재 revision 과 다를 때(409)
     */
    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("사업 건이 다른 곳에서 수정되었습니다. 최신 상태(revision " + revision + ")를 다시 확인하세요");
        }
    }

    /** 사업 개요·부서를 고친다(1단계 작성 중 편집). 제목은 비울 수 없다. */
    public void updateOutline(String title, String summary, String department) {
        if (title == null || title.isBlank()) {
            throw new DomainRuleViolationException("사업명은 필수입니다");
        }
        this.title = title.strip();
        this.summary = summary;
        this.department = department;
    }
}
