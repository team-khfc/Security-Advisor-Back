package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import kr.securityadvisor.auth.controller.MemberController;
import kr.securityadvisor.auth.dto.MemberResponse;
import kr.securityadvisor.auth.service.MemberRegistrationService;
import kr.securityadvisor.common.config.SecurityConfig;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 회원가입 API: 익명 허용, CSRF 필수, 201 + Location, 역할 값 무시, 형식 검증 400, 중복 409. */
@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

    private static final MemberResponse CREATED = new MemberResponse("newuser", "홍길동", MemberRole.APPLICANT);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MemberRegistrationService memberRegistrationService;

    @Test
    @DisplayName("가입 성공 시 201 과 Location 헤더, APPLICANT 역할을 반환한다")
    void signUp() {
        given(memberRegistrationService.register(any())).willReturn(CREATED);

        assertThat(mvc.post()
                        .uri("/api/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"secret1234\",\"displayName\":\"홍길동\"}"))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.LOCATION, "http://localhost/api/members/newuser")
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("APPLICANT");
    }

    @Test
    @DisplayName("요청에 role 을 넣어도 거부하지 않고 무시한다 (서버가 APPLICANT 로 고정)")
    void roleInRequestIsIgnored() {
        given(memberRegistrationService.register(any())).willReturn(CREATED);

        assertThat(mvc.post()
                        .uri("/api/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"secret1234\",\"displayName\":\"홍길동\",\"role\":\"ADMIN\"}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("APPLICANT");
    }

    @Test
    @DisplayName("CSRF 토큰 없는 가입 요청은 403 이다")
    void signUpWithoutCsrf() {
        assertThat(mvc.post()
                        .uri("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"secret1234\",\"displayName\":\"홍길동\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("약한 비밀번호(숫자 없음)·짧은 아이디는 400 으로 거부한다")
    void validation() {
        assertThat(mvc.post()
                        .uri("/api/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"onlyletters\",\"displayName\":\"홍길동\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("아이디 중복은 409 ProblemDetail 이다")
    void duplicate() {
        given(memberRegistrationService.register(any())).willThrow(new ConflictException("이미 사용 중인 아이디입니다"));

        assertThat(mvc.post()
                        .uri("/api/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"applicant\",\"password\":\"secret1234\",\"displayName\":\"홍길동\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("이미 사용 중인 아이디입니다");
    }
}
