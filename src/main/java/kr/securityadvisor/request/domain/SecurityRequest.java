package kr.securityadvisor.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Map;
import java.util.Set;
import kr.securityadvisor.common.entity.BaseAuditEntity;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.DocTypeCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 신청(서류 1건) 애그리거트 루트. 4단계 절차·차수 반환·종결·철회의 상태 전이를 도메인 메서드로만 한다(불변식 10·33·35).
 *
 * <p>사업 건은 {@code businessCaseId} 로만 참조한다(별개 애그리거트). 답변·제출본은 ID 로 이 신청을 가리키며,
 * 제출 조건에 필요한 사실은 {@link SubmissionPreconditions} 로 받는다.
 */
@Entity
@Table(name = "security_requests")
public class SecurityRequest extends BaseAuditEntity {

    /** 허용되는 차수 반환(현재 단계 → 돌아갈 단계): 2→1, 3→2, 3→1, 4→3. */
    private static final Map<Integer, Set<Integer>> ALLOWED_RETURNS =
            Map.of(2, Set.of(1), 3, Set.of(1, 2), 4, Set.of(3));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "security_request_id")
    private Long id;

    @Column(name = "business_case_id", nullable = false)
    private Long businessCaseId;

    /** 서류 유형 코드. 허용 값은 사업 건이 고정한 게시본의 docTypes 가 정한다(ADR 0005) — DB CHECK 없음. */
    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status;

    /** 낙관적 잠금 버전. 클라이언트는 expectedRevision 으로 보내고 불일치는 409(불변식 10). */
    @Version
    @Column(nullable = false)
    private long revision;

    /** 연장 신청이면 원 방화벽 규칙 ID(F-15). 아니면 null. */
    @Column(name = "renews_firewall_rule_id")
    private Long renewsFirewallRuleId;

    /** JPA 전용 기본 생성자. */
    protected SecurityRequest() {}

    private SecurityRequest(Long businessCaseId, DocTypeCode docType) {
        this.businessCaseId = businessCaseId;
        this.docType = docType.code();
        this.stage = 1;
        this.status = RequestStatus.DRAFTING;
    }

    /** 사업 건 생성 시 선택한 서류 유형마다 하나씩 만든다. 1단계 작성 중으로 시작한다. */
    public static SecurityRequest create(Long businessCaseId, DocTypeCode docType) {
        return new SecurityRequest(businessCaseId, docType);
    }

    /** 방화벽 규칙 연장 신청: 원 규칙을 참조하는 새 신청(값은 호출자가 미리 채운다). */
    public static SecurityRequest createRenewal(Long businessCaseId, DocTypeCode docType, Long firewallRuleId) {
        SecurityRequest request = new SecurityRequest(businessCaseId, docType);
        request.renewsFirewallRuleId = firewallRuleId;
        return request;
    }

    public Long getRenewsFirewallRuleId() {
        return renewsFirewallRuleId;
    }

    public Long getId() {
        return id;
    }

    public Long getBusinessCaseId() {
        return businessCaseId;
    }

    public DocTypeCode getDocType() {
        return DocTypeCode.of(docType);
    }

    public int getStage() {
        return stage;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public long getRevision() {
        return revision;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    /** 제출 전(1~3단계)이라 공통 답변 변경의 영향을 받는 신청인지. */
    public boolean isUnsubmitted() {
        return !isTerminal() && stage <= 3;
    }

    /**
     * 오래된 화면에서의 쓰기를 막는다.
     *
     * @throws ConflictException expectedRevision 이 현재 revision 과 다를 때(409)
     */
    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("신청이 다른 곳에서 수정되었습니다. 최신 상태(revision " + revision + ")를 다시 확인하세요");
        }
    }

    /** 1단계 작성 중 → 2단계 협의 중(신청자의 "협의 요청"). 배정·기한 생성은 호출한 서비스가 이어서 한다. */
    public void requestReview() {
        requireState(1, RequestStatus.DRAFTING, "협의 요청은 1단계 작성 중인 신청에서만 할 수 있습니다");
        moveTo(2, RequestStatus.IN_REVIEW);
    }

    /** 담당자의 보완 요청: 2단계 협의 중 또는 3단계 최종 검토 중 → 같은 단계의 보완 요청. */
    public void requestSupplement() {
        requireNotTerminal();
        boolean reviewing = stage == 2 && status == RequestStatus.IN_REVIEW;
        boolean finalReviewing = stage == 3 && status == RequestStatus.FINAL_REVIEW;
        if (!reviewing && !finalReviewing) {
            throw new DomainRuleViolationException("보완 요청은 협의 중이거나 최종 검토 중인 신청에만 할 수 있습니다");
        }
        this.status = RequestStatus.SUPPLEMENT_REQUESTED;
    }

    /** 신청자가 보완에 답하면 같은 단계의 원래 상태(협의 중 / 최종 검토 중)로 돌아간다. */
    public void answerSupplement() {
        requireNotTerminal();
        if (status != RequestStatus.SUPPLEMENT_REQUESTED) {
            throw new DomainRuleViolationException("보완 요청 상태가 아닙니다");
        }
        this.status = stage == 2 ? RequestStatus.IN_REVIEW : RequestStatus.FINAL_REVIEW;
    }

    /**
     * 두 분야 협의 완료 → 3단계 사용자 최종 검토. 할당 완료가 아니라 협의 완료 기준이다(불변식 12).
     *
     * @param allReviewsCompleted 필요한 분야의 협의가 모두 완료됐는지(review 컨텍스트가 판단한 사실)
     */
    public void completeReviews(boolean allReviewsCompleted) {
        requireState(2, RequestStatus.IN_REVIEW, "협의 완료 전이는 2단계 협의 중인 신청에서만 할 수 있습니다");
        if (!allReviewsCompleted) {
            throw new DomainRuleViolationException("요구되는 모든 분야의 협의가 완료되어야 최종 검토 단계로 넘어갑니다");
        }
        moveTo(3, RequestStatus.FINAL_REVIEW);
    }

    /**
     * 신청자 제출: 3단계 최종 검토 중 → 4단계 제출됨. 제출 전 검사(불변식 1·2·3)를 전부 통과해야 한다.
     *
     * @throws DomainRuleViolationException 단계가 맞지 않거나 검사에 걸릴 때(422)
     */
    public void submit(SubmissionPreconditions preconditions) {
        requireState(3, RequestStatus.FINAL_REVIEW, "제출은 3단계 최종 검토 중인 신청에서만 할 수 있습니다");
        if (!preconditions.missingRequiredFields().isEmpty()) {
            throw new DomainRuleViolationException(
                    "필수 항목이 비어 있습니다: " + String.join(", ", preconditions.missingRequiredFields()));
        }
        if (!preconditions.unconfirmedFields().isEmpty()) {
            throw new DomainRuleViolationException(
                    "미확정 값이 있어 제출할 수 없습니다: " + String.join(", ", preconditions.unconfirmedFields()));
        }
        if (preconditions.openSupplementCount() > 0) {
            throw new DomainRuleViolationException("미해결 보완 요청이 " + preconditions.openSupplementCount() + "건 있습니다");
        }
        if (!preconditions.allReviewsCompleted()) {
            throw new DomainRuleViolationException("필요한 분야의 협의가 모두 완료되지 않았습니다");
        }
        moveTo(4, RequestStatus.SUBMITTED);
    }

    /**
     * 4단계 확정: INFRA·SECURITY 최종 확인이 모두 끝난 뒤 배정된 SECURITY 담당자만(불변식 34 — 담당자 검사는 서비스).
     *
     * @param allFinalConfirmed 같은 제출본에 이 서류가 요구하는 모든 분야의 최종 확인이 있는지
     */
    public void confirm(boolean allFinalConfirmed) {
        requireState(4, RequestStatus.SUBMITTED, "확정은 4단계 제출됨 상태에서만 할 수 있습니다");
        if (!allFinalConfirmed) {
            throw new DomainRuleViolationException("요구되는 모든 분야의 최종 확인이 끝나야 확정할 수 있습니다");
        }
        this.status = RequestStatus.CONFIRMED;
    }

    /**
     * 차수 반환(2→1, 3→2, 3→1, 4→3). 사유 필수, 기존 값·이력은 보존한다(불변식 11). 영향 항목 표시는 서비스가 답변에 한다.
     *
     * @throws DomainRuleViolationException 허용되지 않는 반환이거나 사유가 없을 때
     */
    public void returnToStage(int targetStage, String reason) {
        requireNotTerminal();
        requireReason(reason, "차수 반환");
        Set<Integer> targets = ALLOWED_RETURNS.getOrDefault(stage, Set.of());
        if (!targets.contains(targetStage)) {
            throw new DomainRuleViolationException(stage + "단계에서 " + targetStage + "단계로는 반환할 수 없습니다");
        }
        moveTo(targetStage, RequestStatus.firstOf(targetStage));
    }

    /** 담당자 종결(반려). 사유 필수, 이후 수정 불가(불변식 13). */
    public void close(String reason) {
        requireNotTerminal();
        requireReason(reason, "종결");
        this.status = RequestStatus.CLOSED;
    }

    /** 신청자 철회. 사유 필수, 이후 수정 불가(불변식 13). */
    public void withdraw(String reason) {
        requireNotTerminal();
        requireReason(reason, "철회");
        this.status = RequestStatus.WITHDRAWN;
    }

    private void moveTo(int newStage, RequestStatus newStatus) {
        if (!newStatus.allowsStage(newStage)) {
            throw new DomainRuleViolationException("허용되지 않는 단계×상태 조합입니다: " + newStage + "/" + newStatus);
        }
        this.stage = newStage;
        this.status = newStatus;
    }

    private void requireState(int expectedStage, RequestStatus expectedStatus, String message) {
        requireNotTerminal();
        if (stage != expectedStage || status != expectedStatus) {
            throw new DomainRuleViolationException(message + " (현재 " + stage + "단계 " + status.label() + ")");
        }
    }

    private void requireNotTerminal() {
        if (isTerminal()) {
            throw new DomainRuleViolationException("이미 " + status.label() + "된 신청은 변경할 수 없습니다");
        }
    }

    private static void requireReason(String reason, String action) {
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException(action + "에는 사유가 필요합니다");
        }
    }
}
