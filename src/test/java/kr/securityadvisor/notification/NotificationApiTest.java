package kr.securityadvisor.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
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

/** F-17: 협의 요청(배정) 시 담당자에게 알림이 생기고, 수신자 본인만 보고 읽음 처리한다. 업무 상태는 바뀌지 않는다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication SECURITY = auth("security", "정보안", MemberRole.SECURITY);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("배정 알림 → 미읽음 수 → 읽음 처리(PATCH) → 남의 알림은 404")
    void assignedNotification() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"알림 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.CREATED);

        MvcTestResult mine = mvc.get()
                .uri("/api/notifications?unreadOnly=true")
                .with(authentication(INFRA))
                .exchange();
        assertThat(mine).hasStatusOk();
        List<Integer> ids = JsonPath.read(
                mine.getResponse().getContentAsString(),
                "$[?(@.kind=='ASSIGNED' && @.link=='/api/security-requests/" + requestId + "')].notificationId");
        assertThat(ids).hasSize(1);
        assertThat(mvc.get().uri("/api/notifications/unread-count").with(authentication(INFRA)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.unread")
                .asNumber()
                .satisfies(n -> assertThat(n.longValue()).isGreaterThanOrEqualTo(1));

        assertThat(mvc.patch()
                        .uri("/api/notifications/{id}", ids.get(0))
                        .with(authentication(SECURITY))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\":true}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.patch()
                        .uri("/api/notifications/{id}", ids.get(0))
                        .with(authentication(INFRA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\":true}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.readAt")
                .isNotNull();
        assertThat(mvc.get().uri("/api/security-requests/{id}", requestId).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("IN_REVIEW");
    }

    @Test
    @DisplayName("기본 담당자 본인이 신청자면 그 분야는 미배정 → 기관 관리자에게 UNASSIGNED 알림(링크는 배정 자원)")
    void unassignedNotifiesAdmin() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"담당자 본인 신청\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        Long requestId = ((Number)
                        JsonPath.read(created.getResponse().getContentAsString(), "$.requests[0].securityRequestId"))
                .longValue();
        MvcTestResult requested = mvc.post()
                .uri("/api/security-requests/{id}/reviews", requestId)
                .with(authentication(INFRA))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}")
                .exchange();
        assertThat(requested).hasStatus(HttpStatus.CREATED);
        assertThat(requested)
                .bodyJson()
                .extractingPath("$.assignments.unassigned")
                .isEqualTo(List.of("INFRA"));
        assertThat(mvc.get().uri("/api/notifications").with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath(
                        "$[?(@.kind=='UNASSIGNED' && @.link=='/api/security-requests/" + requestId + "/assignments')]")
                .asArray()
                .hasSize(1);
    }

    @Test
    @DisplayName("외부 알림 채널 상태: 설정이 없으면 메일·웹훅 모두 꺼짐(화면 안 알림만), 관리자만 조회")
    void externalChannelsDisabledByDefault() {
        assertThat(mvc.get().uri("/api/notification-channels").with(authentication(INFRA)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/notification-channels").with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.enabled==true)]")
                .asArray()
                .isEmpty();
        assertThat(mvc.get().uri("/api/notification-channels").with(authentication(ADMIN)))
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactlyInAnyOrder("EMAIL", "WEBHOOK");
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
