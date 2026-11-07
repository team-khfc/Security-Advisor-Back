package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import kr.securityadvisor.auth.domain.LoginLockPolicy;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.domain.SignupSource;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Member 애그리거트의 불변식 — 회원가입 역할 고정(불변식 32)과 로그인 실패 잠금 규칙. 프레임워크 없이 검증한다. */
class MemberTest {

    private static final LoginLockPolicy POLICY = new LoginLockPolicy(3, Duration.ofMinutes(15));
    private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");

    @Test
    @DisplayName("회원가입 계정은 항상 APPLICANT·SELF 로 만들어진다")
    void signUpIsAlwaysApplicant() {
        Member member = Member.signUp("newuser", "{bcrypt}hash", "홍길동", "정보화부");

        assertThat(member.getRole()).isEqualTo(MemberRole.APPLICANT);
        assertThat(member.getSignupSource()).isEqualTo(SignupSource.SELF);
        assertThat(member.getDepartment()).isEqualTo("정보화부");
        assertThat(member.canLogin()).isTrue();
        assertThat(member.isLocked(NOW)).isFalse();
    }

    @Test
    @DisplayName("시드 계정은 지정한 역할과 SEED 출처를 가진다")
    void seedKeepsRole() {
        Member member = Member.seed("admin", "{bcrypt}hash", "최관리", MemberRole.ADMIN);

        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(member.getSignupSource()).isEqualTo(SignupSource.SEED);
    }

    @Test
    @DisplayName("상한 미만의 실패는 잠그지 않고 횟수만 센다")
    void failuresBelowLimitDoNotLock() {
        Member member = Member.seed("applicant", "{bcrypt}hash", "김신청", MemberRole.APPLICANT);

        assertThat(member.recordLoginFailure(NOW, POLICY)).isFalse();
        assertThat(member.recordLoginFailure(NOW, POLICY)).isFalse();
        assertThat(member.getFailedLoginCount()).isEqualTo(2);
        assertThat(member.isLocked(NOW)).isFalse();
    }

    @Test
    @DisplayName("상한에 도달하면 잠금 시간 동안 잠기고, 지나면 풀린다")
    void lockAtLimitAndExpire() {
        Member member = Member.seed("applicant", "{bcrypt}hash", "김신청", MemberRole.APPLICANT);
        member.recordLoginFailure(NOW, POLICY);
        member.recordLoginFailure(NOW, POLICY);

        assertThat(member.recordLoginFailure(NOW, POLICY)).isTrue();
        assertThat(member.isLocked(NOW)).isTrue();
        assertThat(member.isLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(member.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    @DisplayName("잠금이 풀린 뒤의 실패는 1회부터 다시 센다")
    void countRestartsAfterLockExpires() {
        Member member = Member.seed("applicant", "{bcrypt}hash", "김신청", MemberRole.APPLICANT);
        for (int i = 0; i < 3; i++) {
            member.recordLoginFailure(NOW, POLICY);
        }
        Instant later = NOW.plus(Duration.ofMinutes(16));

        assertThat(member.recordLoginFailure(later, POLICY)).isFalse();
        assertThat(member.getFailedLoginCount()).isEqualTo(1);
        assertThat(member.isLocked(later)).isFalse();
    }

    @Test
    @DisplayName("로그인 성공은 실패 횟수와 잠금을 초기화한다")
    void successResets() {
        Member member = Member.seed("applicant", "{bcrypt}hash", "김신청", MemberRole.APPLICANT);
        member.recordLoginFailure(NOW, POLICY);
        member.recordLoginFailure(NOW, POLICY);

        member.recordLoginSuccess();

        assertThat(member.getFailedLoginCount()).isZero();
        assertThat(member.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("정책 값이 0 이하이면 만들 수 없다")
    void policyRejectsInvalidValues() {
        assertThatThrownBy(() -> new LoginLockPolicy(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginLockPolicy(3, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
