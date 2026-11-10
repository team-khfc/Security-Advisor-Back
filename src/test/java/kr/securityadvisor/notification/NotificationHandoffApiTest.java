package kr.securityadvisor.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.notification.domain.NotificationKind;
import kr.securityadvisor.notification.service.Notifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 커밋 경계에서 인계 수신자·페이지·읽음·현재 업무의 일치를 검증한다. */
@Tag("docker")
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationHandoffApiTest {
    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Notifier notifier;

    @Autowired
    TransactionTemplate transaction;

    @Test
    @DisplayName("재배정 수신자 변경·130건 페이지·읽음 멱등·권한 상실 후 목록과 건수에서 제외")
    void handoffAndPages() throws Exception {
        String old = register(), next = register();
        long id = request();
        assign(id, old);
        assertThat(number(get(old, "/api/todo-summary"), "$.total")).isEqualTo(1);
        assign(id, next);
        assertThat(number(get(old, "/api/notifications"), "$.totalElements")).isZero();
        assertThat(number(get(old, "/api/notifications/unread-count"), "$.unread"))
                .isZero();
        assertThat(number(get(old, "/api/todos"), "$.totalElements")).isZero();
        long first = number(get(next, "/api/notifications"), "$.content[0].notificationId");
        assertThat(mvc.patch()
                        .uri("/api/notifications/" + first)
                        .with(authentication(auth(old)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\":true}"))
                .hasStatus(404);
        for (int i = 0; i < 130; i++)
            notifier.notify(next, "page:" + id + ":" + i, NotificationKind.ASSIGNED, "합성 알림 " + i, "/requests/" + id);
        assertThat(number(get(next, "/api/notifications?size=100"), "$.totalElements"))
                .isEqualTo(131);
        assertThat(JsonPath.<List<Object>>read(get(next, "/api/notifications?page=1&size=100"), "$.content"))
                .hasSize(31);
        assertThat(JsonPath.<List<Object>>read(get(next, "/api/notifications?page=2&size=100"), "$.content"))
                .isEmpty();
        String readAt = null;
        for (int i = 0; i < 2; i++) {
            var result = mvc.patch()
                    .uri("/api/notifications/" + first)
                    .with(authentication(auth(next)))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"read\":true}")
                    .exchange();
            assertThat(result).hasStatusOk();
            String current = JsonPath.read(result.getResponse().getContentAsString(), "$.readAt");
            if (i == 0) readAt = current;
            else assertThat(current).isEqualTo(readAt);
        }
        assertThat(number(get(next, "/api/notifications?unreadOnly=true"), "$.totalElements"))
                .isEqualTo(130);
        assertThat(number(get(next, "/api/notifications/unread-count"), "$.unread"))
                .isEqualTo(130);
        assertThat(number(get(next, "/api/todos"), "$.totalElements")).isEqualTo(1);
        Authentication stale = auth(next);
        put("/api/members/" + next + "/role", "{\"role\":\"SECURITY\",\"reason\":\"합성 전보\"}");
        assertThat(mvc.get().uri("/api/notifications").with(authentication(stale)))
                .hasStatus(401);
        assertThat(number(get(next, "/api/notifications"), "$.totalElements")).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE security_request_id=? AND kind='REASSIGNMENT_REQUIRED'",
                        Long.class,
                        id))
                .isGreaterThan(0);
        assertThat(mvc.get().uri("/api/notifications?page=-1").with(authentication(auth(next))))
                .hasStatus(400);
        assertThat(mvc.get().uri("/api/todos?size=101").with(authentication(auth(next))))
                .hasStatus(400);
    }

    @Test
    @DisplayName("알림은 업무 커밋 후에만 생성하고 롤백·중복·저장 실패가 업무 결과를 바꾸지 않는다")
    void commitAndFailure() throws Exception {
        String user = register();
        long id = request();
        assign(id, user);
        String key = "commit:" + id;
        transaction.executeWithoutResult(status -> {
            notifier.notify(user, key, NotificationKind.ASSIGNED, "합성 커밋 알림", "/requests/" + id);
            assertThat(count(key)).isZero();
            status.setRollbackOnly();
        });
        assertThat(count(key)).isZero();
        transaction.executeWithoutResult(
                status -> notifier.notify(user, key, NotificationKind.ASSIGNED, "합성 커밋 알림", "/requests/" + id));
        notifier.notify(user, key, NotificationKind.ASSIGNED, "합성 커밋 알림", "/requests/" + id);
        assertThat(count(key)).isEqualTo(1);
        transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE security_requests SET revision=revision+1 WHERE security_request_id=?", id);
            notifier.notify(user, "x".repeat(201), NotificationKind.ASSIGNED, "합성 실패 알림", "/requests/" + id);
        });
        assertThat(jdbc.queryForObject(
                        "SELECT revision FROM security_requests WHERE security_request_id=?", Long.class, id))
                .isGreaterThan(0);
    }

    private long count(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE event_key=?", Long.class, key);
    }

    private String register() throws Exception {
        String user = "nt-" + UUID.randomUUID().toString().substring(0, 8);
        post(
                null,
                "/api/members",
                "{\"username\":\"" + user + "\",\"displayName\":\"합성 알림 담당자\",\"password\":\"Synthetic-pass1\"}");
        put("/api/members/" + user + "/role", "{\"role\":\"INFRA\",\"reason\":\"합성 검증\"}");
        post(
                "admin",
                "/api/reviewer-pools",
                "{\"field\":\"INFRA\",\"memberUsername\":\"" + user + "\",\"defaultReviewer\":false}");
        return user;
    }

    private long request() throws Exception {
        long id = number(
                post("applicant", "/api/business-cases", "{\"title\":\"합성 알림 신청\",\"docTypes\":[\"FIREWALL\"]}"),
                "$.requests[0].securityRequestId");
        post("applicant", "/api/security-requests/" + id + "/reviews", "{\"expectedRevision\":0}");
        return id;
    }

    private void assign(long id, String user) throws Exception {
        post(
                "admin",
                "/api/security-requests/" + id + "/assignments",
                "{\"field\":\"INFRA\",\"assigneeUsername\":\"" + user + "\",\"reason\":\"합성 인계\"}");
    }

    private Authentication auth(String user) {
        var m = jdbc.queryForMap("SELECT role,session_version FROM members WHERE username=?", user);
        MemberRole role = MemberRole.valueOf((String) m.get("role"));
        return new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(user, user, role, ((Number) m.get("session_version")).longValue()),
                null,
                List.of(new SimpleGrantedAuthority(role.authority())));
    }

    private String get(String user, String path) throws Exception {
        var r = mvc.get().uri(path).with(authentication(auth(user))).exchange();
        assertThat(r).hasStatusOk();
        return r.getResponse().getContentAsString();
    }

    private String post(String user, String path, String body) throws Exception {
        var req = mvc.post()
                .uri(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (user != null) req.with(authentication(auth(user)));
        var r = req.exchange();
        assertThat(r).hasStatus(201);
        return r.getResponse().getContentAsString();
    }

    private void put(String path, String body) {
        assertThat(mvc.put()
                        .uri(path)
                        .with(authentication(auth("admin")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .hasStatusOk();
    }

    private long number(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }
}
