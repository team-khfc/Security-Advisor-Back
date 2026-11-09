package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** F-11: 역할 부여는 기관 관리자만, 자기 자신·마지막 관리자는 보호(불변식 32). */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberAdminApiTest {

    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("가입 계정(APPLICANT)에 관리자가 INFRA 역할을 주고, 멱등·권한·보호 규칙을 지킨다")
    void changeRole() {
        signUp("role.target1");

        // 담당자는 목록·역할 변경 불가
        assertThat(mvc.get().uri("/api/members").with(authentication(SECURITY))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(changeRole(SECURITY, "role.target1", "INFRA")).hasStatus(HttpStatus.FORBIDDEN);

        assertThat(changeRole(ADMIN, "role.target1", "INFRA"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("INFRA");
        // 멱등
        assertThat(changeRole(ADMIN, "role.target1", "INFRA"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("INFRA");
        assertThat(mvc.get().uri("/api/members?role=INFRA").with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.username=='role.target1')].role")
                .isEqualTo(List.of("INFRA"));
        assertThat(mvc.get().uri("/api/members/{u}", "role.target1").with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.signupSource")
                .isEqualTo("SELF");

        // 자기 자신 변경·마지막 활성 관리자 강등은 422, 없는 계정은 404, 사유 없음은 400
        assertThat(changeRole(ADMIN, "admin", "APPLICANT")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(changeRole(ADMIN, "no.such.user", "INFRA")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.put()
                        .uri("/api/members/{u}/role", "role.target1")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SECURITY\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        // 연락처(메일 알림 주소): 본인 변경 200, 형식 오류 400, 남의 것은 관리자만
        assertThat(mvc.put()
                        .uri("/api/members/{u}/contact", "role.target1")
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"synthetic@demo.invalid\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.put()
                        .uri("/api/members/{u}/contact", "role.target1")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.put()
                        .uri("/api/members/{u}/contact", "role.target1")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"synthetic@demo.invalid\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("synthetic@demo.invalid");

        // 활성/비활성: 비활성화 → 로그인 불가 상태(active=false), 자기 자신은 422, 다시 활성화. role-assignments 는 역할+활성 한 번에
        assertThat(mvc.put()
                        .uri("/api/members/{u}/activation", "role.target1")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"reason\":\"전보(합성)\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.active")
                .isEqualTo(false);
        assertThat(mvc.put()
                        .uri("/api/members/{u}/activation", "admin")
                        .with(authentication(ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"reason\":\"실수\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        MvcTestResult assigned = mvc.post()
                .uri("/api/members/{u}/role-assignments", "role.target1")
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"SECURITY\",\"active\":true,\"reason\":\"복귀(합성)\"}")
                .exchange();
        assertThat(assigned).hasStatus(HttpStatus.CREATED);
        assertThat(assigned).bodyJson().extractingPath("$.role").isEqualTo("SECURITY");
        assertThat(assigned).bodyJson().extractingPath("$.active").isEqualTo(true);
        // 세션 연장 자원: 만료 예정 시각을 돌려준다
        assertThat(mvc.post()
                        .uri("/api/sessions/current/renewals")
                        .with(authentication(ADMIN))
                        .with(csrf()))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.expiresAt")
                .isNotNull();

        // 두 번째 관리자를 만들면 그 사람을 다시 강등할 수 있다(마지막 관리자가 아니므로)
        assertThat(changeRole(ADMIN, "role.target1", "ADMIN")).hasStatusOk();
        assertThat(changeRole(ADMIN, "role.target1", "APPLICANT"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("APPLICANT");
    }

    private void signUp(String username) {
        assertThat(mvc.post()
                        .uri("/api/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"Synthetic-pass1\",\"displayName\":\"합성 사용자\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult changeRole(Authentication actor, String username, String role) {
        return mvc.put()
                .uri("/api/members/{u}/role", username)
                .with(authentication(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\",\"reason\":\"시연 담당자 지정\"}")
                .exchange();
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
