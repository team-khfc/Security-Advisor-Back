package kr.securityadvisor.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 초안 저장 응답·낙관적 충돌·게시 이력의 불변성을 실제 PostgreSQL에서 검증한다. */
@Tag("docker")
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConfigurationPublishingTest {
    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper mapper;

    private static final Authentication ADMIN = auth("admin", MemberRole.ADMIN);

    @Test
    @DisplayName("잘못된 기한·형식·역할·기본 질문 계약은 저장되지 않는다")
    void invalidDrafts() throws Exception {
        JsonNode current = current();
        ObjectNode fractional = (ObjectNode) current.get("draft").deepCopy();
        ((ObjectNode) fractional.get("deadlinePolicy")).put("reviewBusinessDays", 1.5);
        assertThat(save(current, fractional, "소수 검증")).hasStatus(HttpStatus.BAD_REQUEST);
        for (int days : new int[] {0, -1, 366}) {
            ObjectNode draft = (ObjectNode) current.get("draft").deepCopy();
            ((ObjectNode) draft.get("deadlinePolicy")).put("reviewBusinessDays", days);
            assertThat(save(current, draft, "검증")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        for (String property : List.of("type", "askRole", "key")) {
            ObjectNode draft = (ObjectNode) current.get("draft").deepCopy();
            ((ObjectNode) draft.get("fields").get(0)).put(property, property.equals("askRole") ? "ADMIN" : "invalid");
            assertThat(save(current, draft, "검증")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertThat(current().get("revision")).isEqualTo(current.get("revision"));
        assertThat(current().get("draft")).isEqualTo(current.get("draft"));
    }

    @Test
    @DisplayName("동시에 같은 초안을 저장하면 하나만 성공하고 다른 입력은 409로 보호한다")
    void simultaneousSaves() throws Exception {
        JsonNode current = current();
        ObjectNode first = changed(current, "동시 수정 가");
        ObjectNode second = changed(current, "동시 수정 나");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> {
                ready.countDown();
                start.await();
                return save(current, first, "동시 검증");
            });
            var b = executor.submit(() -> {
                ready.countDown();
                start.await();
                return save(current, second, "동시 검증");
            });
            ready.await();
            start.countDown();
            var responses = List.of(a.get(), b.get());
            assertThat(responses.stream().map(r -> r.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(200, 409);
            JsonNode success = body(responses.stream()
                    .filter(r -> r.getResponse().getStatus() == 200)
                    .findFirst()
                    .orElseThrow());
            assertThat(success.get("revision").asLong())
                    .isGreaterThan(current.get("revision").asLong());
            assertThat(current().get("revision")).isEqualTo(success.get("revision"));
            JsonNode conflict = body(responses.stream()
                    .filter(r -> r.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow());
            assertThat(conflict.get("currentRevision")).isEqualTo(success.get("revision"));
        }
    }

    @Test
    @DisplayName("검토한 초안이 바뀌면 게시를 막고 게시 이력·비교 결과는 후속 초안과 분리한다")
    void publicationHistory() throws Exception {
        JsonNode initial = current();
        JsonNode first = body(save(initial, changed(initial, "게시 검증 질문"), "합성 v2 게시 메모"));
        JsonNode second = body(save(first, changed(first, "게시 검증 질문 수정"), "합성 최종 메모"));
        assertThat(publish(first)).hasStatus(HttpStatus.CONFLICT);
        MvcTestResult result = publish(second);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        JsonNode published = body(result);
        long id = published.get("publicationId").asLong();
        JsonNode comparison = body(mvc.get()
                .uri("/api/publications/{id}/comparison", id)
                .with(authentication(ADMIN))
                .exchange());
        assertThat(comparison.get("changes").size()).isPositive();
        JsonNode history = body(mvc.get()
                .uri("/api/publications?size=1")
                .with(authentication(ADMIN))
                .exchange());
        assertThat(history.get("content").get(0).get("note").asString()).isEqualTo("합성 최종 메모");
        assertThat(history.get("content").get(0).get("publishedBy").asString()).isEqualTo("admin");
        assertThat(history.get("totalElements").asInt()).isGreaterThan(1);
        assertThat(body(mvc.get()
                                .uri("/api/publications?page=1&size=1")
                                .with(authentication(ADMIN))
                                .exchange())
                        .get("content")
                        .size())
                .isEqualTo(1);
        JsonNode latest = current();
        assertThat(publish(latest)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(save(latest, changed(latest, "게시 뒤 미게시 수정"), "후속 초안")).hasStatusOk();
        assertThat(body(mvc.get()
                        .uri("/api/publications/{id}/comparison", id)
                        .with(authentication(ADMIN))
                        .exchange()))
                .isEqualTo(comparison);
        assertThat(body(mvc.get()
                        .uri("/api/publications/{id}", id)
                        .with(authentication(ADMIN))
                        .exchange()))
                .isEqualTo(published);
    }

    @Test
    @DisplayName("신청자와 담당자는 초안·비교·게시 이력에 접근할 수 없다")
    void permissions() throws Exception {
        JsonNode current = current();
        long id = current.get("configurationId").asLong();
        for (String role : List.of("applicant", "infra", "security")) {
            Authentication actor = auth(role, MemberRole.valueOf(role.toUpperCase()));
            for (String path : List.of(
                    "/api/configurations/current",
                    "/api/configurations/" + id + "/comparison",
                    "/api/publications",
                    "/api/publications/1/comparison")) {
                assertThat(mvc.get().uri(path).with(authentication(actor))).hasStatus(HttpStatus.FORBIDDEN);
            }
            assertThat(mvc.post()
                            .uri("/api/configurations/{id}/publications", id)
                            .with(authentication(actor))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedRevision\":0}"))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
        assertThat(mvc.get().uri("/api/publications?size=101").with(authentication(ADMIN)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private JsonNode current() throws Exception {
        return body(mvc.get()
                .uri("/api/configurations/current")
                .with(authentication(ADMIN))
                .exchange());
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private ObjectNode changed(JsonNode current, String label) {
        ObjectNode draft = (ObjectNode) current.get("draft").deepCopy();
        ((ObjectNode) draft.get("fields").get(0)).put("label", label);
        return draft;
    }

    private MvcTestResult save(JsonNode current, JsonNode draft, String note) {
        return mvc.put()
                .uri("/api/configurations/{id}", current.get("configurationId").asLong())
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "draft",
                        draft,
                        "note",
                        note,
                        "expectedRevision",
                        current.get("revision").asLong())))
                .exchange();
    }

    private MvcTestResult publish(JsonNode current) {
        return mvc.post()
                .uri(
                        "/api/configurations/{id}/publications",
                        current.get("configurationId").asLong())
                .with(authentication(ADMIN))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        Map.of("expectedRevision", current.get("revision").asLong())))
                .exchange();
    }

    private static Authentication auth(String name, MemberRole role) {
        return new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(name, "합성", role), null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
