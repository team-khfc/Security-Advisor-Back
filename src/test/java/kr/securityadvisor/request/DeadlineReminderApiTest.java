package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.calendar.BusinessCalendar;
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

/** F-13·F-17: 기한 1영업일 전·경과 알림이 담당자에게 한 번만 가고, 업무 상태는 바뀌지 않는다. 기준일을 지정해 결정적으로 검증한다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeadlineReminderApiTest {

    private static final Authentication APPLICANT = auth("applicant", "김신청", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", "박인프라", MemberRole.INFRA);
    private static final Authentication ADMIN = auth("admin", "최관리", MemberRole.ADMIN);

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("협의 기한 1영업일 전 → DEADLINE_IMMINENT, 기한 다음 날 → DEADLINE_OVERDUE, 재실행해도 중복 없음, 관리자만 실행")
    void remind() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/business-cases")
                .with(authentication(APPLICANT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"title\":\"기한 알림 테스트\",\"summary\":\"개요\",\"department\":\"정보화부\",\"docTypes\":[\"FIREWALL\"]}")
                .exchange();
        String body = created.getResponse().getContentAsString();
        Long requestId = ((Number) JsonPath.read(body, "$.requests[0].securityRequestId")).longValue();
        Long publicationId = ((Number) JsonPath.read(body, "$.publicationId")).longValue();
        assertThat(mvc.post()
                        .uri("/api/security-requests/{id}/reviews", requestId)
                        .with(authentication(APPLICANT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .hasStatus(HttpStatus.CREATED);

        String deadlines = mvc.get()
                .uri("/api/security-requests/{id}/deadlines", requestId)
                .with(authentication(APPLICANT))
                .exchange()
                .getResponse()
                .getContentAsString();
        List<String> dues =
                JsonPath.read(deadlines, "$[?(@.kind=='REVIEW' && @.field=='INFRA' && @.active==true)].dueDate");
        LocalDate due = LocalDate.parse(dues.get(0));

        // 기관 공휴일을 읽어 "기한 1영업일 전" 날짜를 계산한다
        String publication = mvc.get()
                .uri("/api/publications/{id}", publicationId)
                .with(authentication(APPLICANT))
                .exchange()
                .getResponse()
                .getContentAsString();
        List<String> holidayStrings = JsonPath.read(publication, "$.fieldSchema.deadlinePolicy.holidays[*]");
        Set<LocalDate> holidays = new HashSet<>();
        holidayStrings.forEach(h -> holidays.add(LocalDate.parse(h)));
        BusinessCalendar calendar = new BusinessCalendar(holidays);
        LocalDate dayBefore = due.minusDays(1);
        while (!calendar.addBusinessDays(dayBefore, 1).equals(due)) {
            dayBefore = dayBefore.minusDays(1);
        }

        assertThat(run(INFRA, dayBefore)).hasStatus(HttpStatus.FORBIDDEN);

        MvcTestResult imminent = run(ADMIN, dayBefore);
        assertThat(imminent).hasStatus(HttpStatus.CREATED);
        assertThat(imminent).bodyJson().extractingPath("$.asOf").isEqualTo(dayBefore.toString());
        assertThat(((Number) JsonPath.read(imminent.getResponse().getContentAsString(), "$.imminent")).intValue())
                .isGreaterThanOrEqualTo(1);
        assertThat(countNotifications(INFRA, "DEADLINE_IMMINENT", requestId)).isEqualTo(1);
        // 같은 기준일로 다시 실행해도 알림은 한 번
        assertThat(run(ADMIN, dayBefore)).hasStatus(HttpStatus.CREATED);
        assertThat(countNotifications(INFRA, "DEADLINE_IMMINENT", requestId)).isEqualTo(1);

        // 기한 다음 날 → 경과 알림, 신청 상태는 그대로
        assertThat(run(ADMIN, due.plusDays(1))).hasStatus(HttpStatus.CREATED);
        assertThat(countNotifications(INFRA, "DEADLINE_OVERDUE", requestId)).isEqualTo(1);
        assertThat(countNotifications(APPLICANT, "DEADLINE_OVERDUE", requestId)).isEqualTo(0);
        assertThat(mvc.get().uri("/api/security-requests/{id}", requestId).with(authentication(APPLICANT)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("IN_REVIEW");
    }

    @Test
    @DisplayName("스케줄은 관리자만 조회·변경, cron 형식 오류 422, 변경 즉시 다음 실행 시각 반영, 끄면 null, 끝에 기본값 복원")
    void schedule() throws Exception {
        assertThat(mvc.get().uri("/api/deadline-reminder-schedule").with(authentication(INFRA)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/deadline-reminder-schedule").with(authentication(ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.timezone")
                .isEqualTo("Asia/Seoul");
        assertThat(changeSchedule("매일 아침", true)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        MvcTestResult changed = changeSchedule("0 0 9 * * 1-5", true);
        assertThat(changed).hasStatusOk();
        assertThat(changed).bodyJson().extractingPath("$.cron").isEqualTo("0 0 9 * * 1-5");
        assertThat(changed).bodyJson().extractingPath("$.enabled").isEqualTo(true);
        assertThat(changed).bodyJson().extractingPath("$.updatedBy").isEqualTo("admin");
        String next = JsonPath.read(changed.getResponse().getContentAsString(), "$.nextRunAt");
        assertThat(next).contains("T09:00").contains("+09:00");

        MvcTestResult disabled = changeSchedule("0 0 9 * * 1-5", false);
        assertThat(disabled).hasStatusOk();
        assertThat(disabled).bodyJson().extractingPath("$.enabled").isEqualTo(false);
        assertThat(disabled).bodyJson().extractingPath("$.nextRunAt").isNull();

        // 다른 테스트 컨텍스트와 DB 를 공유하므로 seed 값으로 되돌린다
        assertThat(changeSchedule("0 10 8 * * *", true))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.nextRunAt")
                .isNotNull();
    }

    private MvcTestResult changeSchedule(String cron, boolean enabled) {
        return mvc.put()
                .uri("/api/deadline-reminder-schedule")
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cron\":\"" + cron + "\",\"enabled\":" + enabled + "}")
                .exchange();
    }

    private MvcTestResult run(Authentication who, LocalDate asOf) {
        return mvc.post()
                .uri("/api/deadline-reminder-runs")
                .with(authentication(who))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"asOf\":\"" + asOf + "\"}")
                .exchange();
    }

    private int countNotifications(Authentication who, String kind, Long requestId) throws Exception {
        String body = mvc.get()
                .uri("/api/notifications")
                .with(authentication(who))
                .exchange()
                .getResponse()
                .getContentAsString();
        List<Object> hits = JsonPath.read(
                body, "$[?(@.kind=='" + kind + "' && @.link=='/api/security-requests/" + requestId + "/deadlines')]");
        return hits.size();
    }

    private static Authentication auth(String username, String displayName, MemberRole role) {
        MemberPrincipal principal = new MemberPrincipal(username, displayName, role);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
