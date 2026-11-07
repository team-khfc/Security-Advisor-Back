package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.auth.domain.LoginLockPolicy;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.dto.LoginRequest;
import kr.securityadvisor.auth.dto.MemberResponse.LoginResult;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.auth.service.AuthService;
import kr.securityadvisor.auth.service.LoginAttemptRecorder;
import kr.securityadvisor.common.error.TooManyRequestsException;
import kr.securityadvisor.common.error.UnauthorizedException;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 로그인 검증 규칙: 계정 없음·비활성·비밀번호 오류는 같은 401, 잠긴 계정은 429. 실패·성공은 기록기로 넘긴다. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginAttemptRecorder loginAttemptRecorder;

    @Mock
    private AuditRecorder auditRecorder;

    private AuthService authService;

    private Member member;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                memberRepository,
                passwordEncoder,
                loginAttemptRecorder,
                auditRecorder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        member = Member.seed("applicant", "{bcrypt}hash", "김신청", MemberRole.APPLICANT);
    }

    @Test
    @DisplayName("아이디·비밀번호가 맞으면 principal 과 사용자 정보를 돌려주고 성공을 기록한다")
    void loginSuccess() {
        given(memberRepository.findById("applicant")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password1!", "{bcrypt}hash")).willReturn(true);

        LoginResult result = authService.login(new LoginRequest("applicant", "password1!"));

        assertThat(result.principal().username()).isEqualTo("applicant");
        assertThat(result.principal().role()).isEqualTo(MemberRole.APPLICANT);
        assertThat(result.member().displayName()).isEqualTo("김신청");
        then(loginAttemptRecorder).should().recordSuccess("applicant");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 실패를 기록하고 UnauthorizedException 을 던진다")
    void wrongPassword() {
        given(memberRepository.findById("applicant")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", "{bcrypt}hash")).willReturn(false);
        given(loginAttemptRecorder.recordFailure("applicant", NOW)).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("applicant", "wrong")))
                .isInstanceOf(UnauthorizedException.class);
        then(loginAttemptRecorder).should(never()).recordSuccess(anyString());
    }

    @Test
    @DisplayName("이번 실패로 상한에 도달하면 TooManyRequestsException(429) 을 던진다")
    void failureThatLocks() {
        given(memberRepository.findById("applicant")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", "{bcrypt}hash")).willReturn(false);
        given(loginAttemptRecorder.recordFailure("applicant", NOW)).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("applicant", "wrong")))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    @DisplayName("잠긴 계정은 비밀번호를 대조하지 않고 TooManyRequestsException 을 던진다")
    void lockedMember() {
        LoginLockPolicy policy = new LoginLockPolicy(1, Duration.ofMinutes(15));
        member.recordLoginFailure(NOW.minus(Duration.ofMinutes(1)), policy);
        given(memberRepository.findById("applicant")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest("applicant", "password1!")))
                .isInstanceOf(TooManyRequestsException.class);
        then(passwordEncoder).should(never()).matches(any(), any());
        then(loginAttemptRecorder).should(never()).recordFailure(anyString(), any());
    }

    @Test
    @DisplayName("없는 아이디는 비밀번호를 대조하지 않고 UnauthorizedException 을 던진다")
    void unknownUser() {
        given(memberRepository.findById("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", "password1!")))
                .isInstanceOf(UnauthorizedException.class);
        then(passwordEncoder).should(never()).matches(any(), any());
    }

    @Test
    @DisplayName("비활성화된 계정은 비밀번호가 맞아도 로그인할 수 없다")
    void deactivatedMember() {
        member.deactivate();
        given(memberRepository.findById("applicant")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest("applicant", "password1!")))
                .isInstanceOf(UnauthorizedException.class);
    }
}
