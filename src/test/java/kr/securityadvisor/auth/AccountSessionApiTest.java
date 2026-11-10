package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.UUID;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 실제 로그인 세션과 DB 계정 변경을 함께 사용해 권한 회수를 검증한다. */
@Tag("docker")
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountSessionApiTest {
    private static final String PASSWORD = "Synthetic-session-123!";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    PasswordEncoder passwords;

    @Test
    @DisplayName("비활성화는 여러 기존 세션의 조회·쓰기·연장을 차단하고 새 로그인도 거부한다")
    void deactivationRevokesEverySession() {
        String user = member(MemberRole.INFRA);
        MockHttpSession first = login(user);
        MockHttpSession second = login(user);
        assertThat(mvc.get().uri("/api/security-requests").session(first)).hasStatusOk();
        activate(user, false);
        assertThat(mvc.get().uri("/api/security-requests").session(first)).hasStatus(401);
        assertThat(mvc.post()
                        .uri("/api/sessions/current/renewals")
                        .session(second)
                        .with(csrf()))
                .hasStatus(401);
        assertThat(loginRequest(user)).hasStatus(401);
    }

    @Test
    @DisplayName("비활성화 후 즉시 재활성화해도 미사용 옛 세션은 복구되지 않고 재로그인만 허용한다")
    void reactivationDoesNotRestoreOldSession() {
        String user = member(MemberRole.APPLICANT);
        MockHttpSession old = login(user);
        activate(user, false);
        activate(user, true);
        assertThat(mvc.post()
                        .uri("/api/business-cases")
                        .session(old)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"차단되어야 할 합성 사업\",\"docTypes\":[\"FIREWALL\"]}"))
                .hasStatus(401);
        assertThat(mvc.get().uri("/api/auth/me").session(login(user))).hasStatusOk();
    }

    @Test
    @DisplayName("관리자 강등 즉시 기존 관리자 API를 차단하고 새 세션은 변경된 역할만 가진다")
    void roleChangeRevokesPrivilege() {
        String user = member(MemberRole.ADMIN);
        MockHttpSession old = login(user);
        assertThat(mvc.get().uri("/api/insights/summary").session(old)).hasStatusOk();
        role(user, MemberRole.APPLICANT);
        assertThat(mvc.get().uri("/api/members").session(old)).hasStatus(401);
        MockHttpSession fresh = login(user);
        assertThat(mvc.get().uri("/api/members").session(fresh)).hasStatus(403);
        assertThat(mvc.get().uri("/api/auth/me").session(fresh))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("APPLICANT");
    }

    @Test
    @DisplayName("역할을 원복해도 옛 세션은 차단하고 같은 역할·활성 상태의 멱등 요청은 세션을 유지한다")
    void roleRoundTripAndNoOp() {
        String user = member(MemberRole.INFRA);
        MockHttpSession old = login(user);
        role(user, MemberRole.SECURITY);
        role(user, MemberRole.INFRA);
        assertThat(mvc.get().uri("/api/auth/me").session(old)).hasStatus(401);
        MockHttpSession fresh = login(user);
        role(user, MemberRole.INFRA);
        activate(user, true);
        assertThat(mvc.get().uri("/api/auth/me").session(fresh)).hasStatusOk();
    }

    private String member(MemberRole role) {
        String username = "session-" + UUID.randomUUID();
        members.saveAndFlush(Member.seed(username, passwords.encode(PASSWORD), "합성 세션 검증", role));
        return username;
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult loginRequest(String username) {
        return mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}")
                .exchange();
    }

    private MockHttpSession login(String user) {
        var result = loginRequest(user);
        assertThat(result).hasStatusOk();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void activate(String user, boolean active) {
        change(user, "activation", "\"active\":" + active);
    }

    private void role(String user, MemberRole role) {
        change(user, "role", "\"role\":\"" + role + "\"");
    }

    private void change(String user, String resource, String value) {
        var admin = UsernamePasswordAuthenticationToken.authenticated(
                new MemberPrincipal("admin", "합성 관리자", MemberRole.ADMIN),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(mvc.put()
                        .uri("/api/members/" + user + "/" + resource)
                        .with(authentication(admin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + value + ",\"reason\":\"합성 세션 검증\"}"))
                .hasStatusOk();
    }
}
