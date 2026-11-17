package kr.securityadvisor.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.util.List;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.security.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Tag("docker")
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActuatorSecurityTest {
    @Autowired
    MockMvcTester mvc;

    @Test
    void 최소_상태만_공개하고_운영지표는_관리자만_조회한다() throws Exception {
        var health = mvc.get().uri("/actuator/health").exchange();
        assertThat(health).hasStatusOk();
        assertThat(health).bodyJson().isEqualTo("{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}");
        assertThat(mvc.get().uri("/actuator/metrics")).hasStatus(HttpStatus.UNAUTHORIZED);
        for (var role : MemberRole.values()) {
            String username = switch (role) {
                case ADMIN -> "admin";
                case INFRA -> "infra";
                case SECURITY -> "security";
                case APPLICANT -> "applicant";
            };
            var auth = new UsernamePasswordAuthenticationToken(
                    new MemberPrincipal(username, "합성", role),
                    null,
                    List.of(new SimpleGrantedAuthority(role.authority())));
            for (String path : List.of("/actuator/metrics", "/actuator/info"))
                assertThat(mvc.get().uri(path).with(authentication(auth)))
                        .hasStatus(role == MemberRole.ADMIN ? HttpStatus.OK : HttpStatus.FORBIDDEN);
            for (String path : List.of(
                    "/actuator/env",
                    "/actuator/configprops",
                    "/actuator/heapdump",
                    "/actuator/loggers",
                    "/actuator/logfile",
                    "/actuator/shutdown",
                    "/actuator"))
                assertThat(mvc.get().uri(path).with(authentication(auth))).hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
