package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.util.List;
import kr.securityadvisor.auth.controller.AuthController;
import kr.securityadvisor.auth.dto.MemberResponse;
import kr.securityadvisor.auth.dto.MemberResponse.LoginResult;
import kr.securityadvisor.auth.service.AuthService;
import kr.securityadvisor.common.config.SecurityConfig;
import kr.securityadvisor.common.error.UnauthorizedException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.common.security.SessionAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 인증 API 와 SecurityConfig 의 HTTP 동작(익명 허용 경로, 401 ProblemDetail)을 확인한다. */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final MemberPrincipal APPLICANT = new MemberPrincipal("applicant", "김신청", MemberRole.APPLICANT);
    private static final MemberResponse APPLICANT_RESPONSE =
            new MemberResponse("applicant", "김신청", MemberRole.APPLICANT);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionAuthenticator sessionAuthenticator;

    @Test
    @DisplayName("로그인 성공 시 200 과 사용자 정보를 반환한다 (익명 접근 허용 경로)")
    void loginSuccess() {
        given(authService.login(any())).willReturn(new LoginResult(APPLICANT, APPLICANT_RESPONSE));

        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"applicant\",\"password\":\"password1!\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("APPLICANT");
    }

    @Test
    @DisplayName("로그인 실패는 401 ProblemDetail 을 반환한다")
    void loginFailure() {
        given(authService.login(any())).willThrow(new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다"));

        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"applicant\",\"password\":\"wrong\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("아이디 또는 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("빈 아이디·비밀번호는 400 으로 거부한다")
    void loginValidation() {
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("로그인하지 않은 상태의 보호 API 요청은 401 ProblemDetail 을 반환한다")
    void meWithoutSession() {
        assertThat(mvc.get().uri("/api/auth/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("인증이 필요합니다");
    }

    @Test
    @DisplayName("로그인한 사용자는 자기 정보를 조회한다")
    void meWithSession() {
        given(authService.getCurrentMember(any())).willReturn(APPLICANT_RESPONSE);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                APPLICANT, null, List.of(new SimpleGrantedAuthority(MemberRole.APPLICANT.authority())));

        assertThat(mvc.get().uri("/api/auth/me").with(authentication(authentication)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.username")
                .isEqualTo("applicant");
    }

    @Test
    @DisplayName("로그아웃은 세션 유무와 관계없이 204 를 반환한다")
    void logout() {
        assertThat(mvc.post().uri("/api/auth/logout")).hasStatus(HttpStatus.NO_CONTENT);
    }
}
