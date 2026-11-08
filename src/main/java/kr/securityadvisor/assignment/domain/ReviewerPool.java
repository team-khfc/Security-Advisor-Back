package kr.securityadvisor.assignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.schema.ReviewField;

/** 분야별 배정 가능 담당자 한 명(기관 설정). 분야당 활성 기본 담당자는 한 명이며 자동 배정 대상이다(F-12). */
@Entity
@Table(name = "reviewer_pools")
public class ReviewerPool extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reviewer_pool_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewField field;

    @Column(name = "member_username", nullable = false, length = 50)
    private String memberUsername;

    @Column(name = "is_default", nullable = false)
    private boolean defaultReviewer;

    @Column(nullable = false)
    private boolean active;

    /** JPA 전용 기본 생성자. */
    protected ReviewerPool() {}

    private ReviewerPool(ReviewField field, String memberUsername) {
        this.field = field;
        this.memberUsername = memberUsername;
        this.defaultReviewer = false;
        this.active = true;
    }

    /** 담당자 목록에 등록한다(활성, 기본 담당자 아님). 기본 담당자 지정은 {@link #markDefault()} 로 따로 한다. */
    public static ReviewerPool register(ReviewField field, String memberUsername) {
        return new ReviewerPool(field, memberUsername);
    }

    /** 이 분야의 기본 담당자로 지정한다. 분야당 활성 기본 담당자 1명은 서비스가 이전 기본을 해제해 지킨다. */
    public void markDefault() {
        if (!active) {
            throw new IllegalStateException("비활성 담당자는 기본 담당자가 될 수 없습니다");
        }
        this.defaultReviewer = true;
    }

    public void unmarkDefault() {
        this.defaultReviewer = false;
    }

    /** 목록에서 비활성화한다. 기본 담당자였다면 그 지정도 풀린다. 삭제하지 않아 재배정 이력과 재활성화가 가능하다. */
    public void deactivate() {
        this.active = false;
        this.defaultReviewer = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public ReviewField getField() {
        return field;
    }

    public String getMemberUsername() {
        return memberUsername;
    }

    public boolean isDefaultReviewer() {
        return defaultReviewer;
    }

    public boolean isActive() {
        return active;
    }
}
