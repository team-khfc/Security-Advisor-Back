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

    /** JPA 전용 기본 생성자. 1차 범위는 seed 로만 만들어진다(편집 화면 P2). */
    protected ReviewerPool() {}

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
