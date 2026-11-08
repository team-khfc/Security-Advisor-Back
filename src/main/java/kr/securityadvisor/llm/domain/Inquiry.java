package kr.securityadvisor.llm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;

/** 담당자 AI 질의 대화(F-04). 신청·담당자 단위이며 본인만 읽고 쓴다. 업무 상태를 바꾸지 않는다. */
@Entity
@Table(name = "inquiries")
public class Inquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    @Column(name = "staff_username", nullable = false, length = 50)
    private String staffUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewField field;

    @Column(length = 200)
    private String title;

    protected Inquiry() {}

    private Inquiry(Long securityRequestId, String staffUsername, ReviewField field, String title) {
        this.securityRequestId = securityRequestId;
        this.staffUsername = staffUsername;
        this.field = field;
        this.title = title;
    }

    public static Inquiry open(Long securityRequestId, String staffUsername, ReviewField field, String title) {
        if (staffUsername == null || staffUsername.isBlank()) {
            throw new DomainRuleViolationException("질의 담당자가 필요합니다");
        }
        if (field == null) {
            throw new DomainRuleViolationException("질의 분야가 필요합니다");
        }
        return new Inquiry(securityRequestId, staffUsername, field, title == null ? null : title.strip());
    }

    public boolean isOwnedBy(String username) {
        return staffUsername.equals(username);
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public String getStaffUsername() {
        return staffUsername;
    }

    public ReviewField getField() {
        return field;
    }

    public String getTitle() {
        return title;
    }
}
