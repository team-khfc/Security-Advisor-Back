package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.domain.SignupSource;
import kr.securityadvisor.auth.dto.MemberResponse;
import kr.securityadvisor.auth.dto.SignupRequest;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.auth.service.MemberRegistrationService;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 회원가입 규칙: 아이디 중복은 409, 비밀번호는 해시로 저장, 역할은 APPLICANT 고정(불변식 32). */
@ExtendWith(MockitoExtension.class)
class MemberRegistrationServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberRegistrationService service;

    @Test
    @DisplayName("새 아이디면 해시된 비밀번호와 APPLICANT·SELF 로 저장한다")
    void register() {
        given(memberRepository.existsById("newuser")).willReturn(false);
        given(passwordEncoder.encode("secret1234")).willReturn("{bcrypt}hashed");
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        MemberResponse response = service.register(new SignupRequest("newuser", "secret1234", "홍길동", "정보화부"));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        then(memberRepository).should().save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hashed");
        assertThat(saved.getValue().getRole()).isEqualTo(MemberRole.APPLICANT);
        assertThat(saved.getValue().getSignupSource()).isEqualTo(SignupSource.SELF);
        assertThat(response.username()).isEqualTo("newuser");
        assertThat(response.role()).isEqualTo(MemberRole.APPLICANT);
    }

    @Test
    @DisplayName("이미 있는 아이디는 ConflictException(409) 을 던지고 저장하지 않는다")
    void duplicateUsername() {
        given(memberRepository.existsById("applicant")).willReturn(true);

        assertThatThrownBy(() -> service.register(new SignupRequest("applicant", "secret1234", "누군가", null)))
                .isInstanceOf(ConflictException.class);
        then(memberRepository).should(never()).save(any());
    }
}
