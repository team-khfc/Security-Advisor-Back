package kr.securityadvisor.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 사용자 계정. 로그인 아이디(username)가 기본 키이며 역할 1개를 가진다.
 *
 * <p>운영 기관은 설치당 1곳 고정이라 기관 개념이 없다(FUNCTIONAL_SPEC 3-4장). 비밀번호는 해시만 저장한다 —
 * 해시 생성·대조는 service 계층(PasswordEncoder)이 하고, 엔티티는 해시 문자열만 보관한다.
 * 로그인 실패 횟수·잠금은 이 애그리거트의 불변식으로 관리한다.
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

    @Column(length = 100)
    private String department;

    /** 메일 알림 수신 주소(선택). 비어 있으면 화면 안 알림만 받는다. */
    @Column(length = 200)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "signup_source", nullable = false, length = 10)
    private SignupSource signupSource;

    /** false 이면 로그인할 수 없다. 이력이 참조하므로 삭제 대신 비활성화한다. */
    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** JPA 전용 기본 생성자. */
    protected Member() {}

    private Member(
            String username,
            String passwordHash,
            String displayName,
            String department,
            MemberRole role,
            SignupSource signupSource) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.department = department;
        this.role = role;
        this.signupSource = signupSource;
        this.active = true;
        this.failedLoginCount = 0;
    }

    /**
     * 회원가입으로 계정을 만든다. 역할은 요청과 무관하게 항상 APPLICANT 다(불변식 32 — 회원가입을 통한 권한 상승 금지).
     * 담당자·관리자 역할은 기관 관리자만 부여한다.
     */
    public static Member signUp(String username, String passwordHash, String displayName, String department) {
        return new Member(username, passwordHash, displayName, department, MemberRole.APPLICANT, SignupSource.SELF);
    }

    /** 시연 계정(seed)을 만든다. 역할은 호출자가 정한다 — 기동 시 DemoAccountSeeder 만 쓴다. */
    public static Member seed(String username, String passwordHash, String displayName, MemberRole role) {
        return new Member(username, passwordHash, displayName, null, role, SignupSource.SEED);
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

    public String getDepartment() {
        return department;
    }

    public String getEmail() {
        return email;
    }

    /** 연락처 변경(본인 또는 기관 관리자). 빈 값이면 해제. 형식 검증은 DTO 가 한다. */
    public void changeEmail(String email) {
        this.email = email == null || email.isBlank() ? null : email.strip();
    }

    public MemberRole getRole() {
        return role;
    }

    public SignupSource getSignupSource() {
        return signupSource;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /** 활성 계정인지. 잠금 여부는 {@link #isLocked(Instant)} 로 따로 본다. */
    public boolean canLogin() {
        return active;
    }

    /** 지금 시각 기준으로 로그인 잠금 중인지. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * 비밀번호 오류를 기록한다. 지난 잠금이 풀린 뒤의 첫 실패는 1회부터 다시 센다.
     * 연속 실패가 정책의 상한에 이르면 잠금 시각을 정한다.
     *
     * @return 이번 실패로 잠금이 걸렸으면 true
     */
    public boolean recordLoginFailure(Instant now, LoginLockPolicy policy) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        if (failedLoginCount >= policy.maxFailedLogins()) {
            lockedUntil = now.plus(policy.lockDuration());
            return true;
        }
        return false;
    }

    /** 로그인 성공: 실패 횟수와 잠금을 초기화한다. */
    public void recordLoginSuccess() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    /** 계정을 비활성화한다. 다시 로그인할 수 없지만 기존 이력은 남는다. */
    public void deactivate() {
        this.active = false;
    }

    /** 비활성 계정을 다시 활성화한다. 잠금·실패 횟수는 초기화한다. */
    public void activate() {
        this.active = true;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    /**
     * 기관 관리자가 역할을 바꾼다(불변식 32 — 역할 부여는 관리자만). 같은 역할이면 아무것도 바꾸지 않는다.
     * 호출자(서비스)가 관리자 권한·마지막 관리자 보호를 검사한 뒤 호출한다.
     *
     * @return 실제로 바뀌었으면 true
     */
    public boolean changeRole(MemberRole newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("역할은 비울 수 없습니다");
        }
        if (this.role == newRole) {
            return false;
        }
        this.role = newRole;
        return true;
    }
}
