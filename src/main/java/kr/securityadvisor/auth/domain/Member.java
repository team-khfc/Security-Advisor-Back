package kr.securityadvisor.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 시연용 사용자 계정. 로그인 아이디(username)가 기본 키이며 역할 1개를 가진다.
 *
 * <p>시스템은 단일 기관 운영이 확정이라 기관 개념이 없다(ADR-0003).
 * 비밀번호는 해시만 저장한다. 해시 생성·대조는 service 계층(PasswordEncoder)이 하고, 엔티티는 해시 문자열만 보관한다.
 */
@Entity
@Table(name = "members")
public class Member extends BaseTimeEntity {

    /** 로그인 아이디이자 기본 키. 다른 테이블은 이 값으로 사용자를 참조한다(created_by 등). */
    @Id
    @Column(length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    /** false 이면 로그인할 수 없다. 이력이 참조하므로 삭제 대신 비활성화한다. */
    @Column(nullable = false)
    private boolean active;

    /** JPA 전용 기본 생성자. */
    protected Member() {}

    public Member(String username, String passwordHash, String displayName, MemberRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.active = true;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MemberRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    /** 로그인 가능한 상태인지. */
    public boolean canLogin() {
        return active;
    }

    /** 계정을 비활성화한다. 다시 로그인할 수 없지만 기존 이력은 남는다. */
    public void deactivate() {
        this.active = false;
    }
}
