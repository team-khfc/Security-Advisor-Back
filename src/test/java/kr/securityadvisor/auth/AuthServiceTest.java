package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.dto.LoginRequest;
import kr.securityadvisor.auth.dto.MemberResponse.LoginResult;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.auth.service.AuthService;
import kr.securityadvisor.common.error.UnauthorizedException;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 로그인 검증 규칙: 계정 없음·비활성·비밀번호 오류를 모두 같은 401 로 처리한다. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private final Member member = new Member("applicant", "{bcrypt}hash", "김신청", MemberRole.APPLICANT);

    @Test
    @DisplayName("아이디·비밀번호가 맞으면 principal 과 사용자 정보를 돌려준다")
    void loginSuccess() {
        given(memberRepository.findById("applicant")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password1!", "{bcrypt}hash")).willReturn(true);

        LoginResult result = authService.login(new LoginRequest("applicant", "password1!"));

        assertThat(result.principal().username()).isEqualTo("applicant");
        assertThat(result.principal().role()).isEqualTo(MemberRole.APPLICANT);
        assertThat(result.member().displayName()).isEqualTo("김신청");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 UnauthorizedException 을 던진다")
    void wrongPassword() {
        given(memberRepository.findById("applicant")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", "{bcrypt}hash")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("applicant", "wrong")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("없는 아이디는 비밀번호를 대조하지 않고 UnauthorizedException 을 던진다")
    void unknownUser() {
        given(memberRepository.findById("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", "password1!")))
                .isInstanceOf(UnauthorizedException.class);
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
