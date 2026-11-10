package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.service.LlmConfigurationSelector;
import kr.securityadvisor.llm.service.LlmConnectionProbe;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Tag("docker")
@SpringBootTest(
        properties = {
            "spring.application.name=llm-administration-test",
            "securityadvisor.demo.account-password=Test-Demo-1234",
            "securityadvisor.llm.mode=fake",
            "OPENROUTER_API_KEY=synthetic-model-key-for-tests"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LlmAdministrationTest {
    @Autowired
    MockMvcTester mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LlmConfigurationSelector selector;

    @MockitoBean
    LlmConnectionProbe probe;

    private static final Authentication ADMIN = auth("admin", MemberRole.ADMIN);

    @Test
    @DisplayName("비교한 구성 적용·이력·복원은 버전을 올리고 과거 사본과 기존 사업을 보존한다")
    void changesAndRestoration() {
        var initial = json(post("/api/llm-configurations", ADMIN, command("history-model", false)), 201);
        long id = initial.path("llmConfigurationId").asLong();
        assertThat(initial.path("active").asBoolean()).isFalse();
        var history = get("/api/llm-configurations/" + id + "/history", ADMIN);
        long first = history.path("content").get(0).path("historyId").asLong();
        ObjectNode changed = edit(initial);
        changed.put("model", "synthetic-v2");
        changed.put("active", true);
        changed.put("priority", 0);
        var compare = json(post("/api/llm-configurations/" + id + "/comparisons", ADMIN, changed), 200);
        assertThat(compare.path("changes").size()).isEqualTo(3);
        assertThat(get("/api/llm-configurations/" + id, ADMIN).path("model").asString())
                .isEqualTo("synthetic-v1");
        var v2 = json(put("/api/llm-configurations/" + id, ADMIN, changed), 200);
        assertThat(v2.path("revision").asLong()).isEqualTo(1);
        assertThat(selector.select(LlmPurpose.INTERVIEW).configuration().getId())
                .isEqualTo(id);
        assertThat(selector.select(LlmPurpose.DOCUMENT).configuration().getId()).isNotEqualTo(id);
        json(put("/api/llm-configurations/" + id, ADMIN, changed), 409);
        var v3 = json(
                post(
                        "/api/llm-configurations/" + id + "/restorations",
                        ADMIN,
                        Map.of("historyId", first, "expectedRevision", 1, "reason", "합성 이전 구성 복원")),
                200);
        assertThat(v3.path("revision").asLong()).isEqualTo(2);
        assertThat(v3.path("model").asString()).isEqualTo("synthetic-v1");
        assertThat(v3.path("active").asBoolean()).isFalse();
        var last = get("/api/llm-configurations/" + id + "/history?page=0&size=1", ADMIN);
        assertThat(last.path("totalElements").asInt()).isEqualTo(3);
        assertThat(last.path("content").get(0).path("restoredFromId").asLong()).isEqualTo(first);
        var old = get("/api/llm-configurations/" + id + "/history?page=2&size=1", ADMIN);
        assertThat(old.path("content").get(0)).isEqualTo(history.path("content").get(0));
        json(
                post(
                        "/api/llm-configurations/" + id + "/restorations",
                        ADMIN,
                        Map.of("historyId", first, "expectedRevision", 0, "reason", "오래된 화면")),
                409);
        assertThatThrownBy(() -> jdbc.update(
                        "delete from llm_configuration_histories where llm_configuration_history_id=?", first))
                .hasMessageContaining("추가 전용");
        assertThat(last.toString()).doesNotContain("synthetic-model-key-for-tests");
    }

    @Test
    @DisplayName("동시 수정은 한 요청만 적용되고 다른 요청은 409이며 이력도 한 번만 추가된다")
    void concurrentEdits() throws Exception {
        var created = json(post("/api/llm-configurations", ADMIN, command("concurrent-model", false)), 201);
        String path =
                "/api/llm-configurations/" + created.path("llmConfigurationId").asLong();
        ObjectNode a = edit(created);
        a.put("model", "synthetic-a");
        ObjectNode b = edit(created);
        b.put("model", "synthetic-b");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                start.await();
                return put(path, ADMIN, a).getResponse().getStatus();
            });
            var two = executor.submit(() -> {
                start.await();
                return put(path, ADMIN, b).getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(get(path + "/history", ADMIN).path("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("연결 시험은 저장하지 않은 값만 시험하고 실패·설정 변경을 명시하며 비밀값을 남기지 않는다")
    void connectionTests() {
        var created = json(post("/api/llm-configurations", ADMIN, command("probe-model", false)), 201);
        long id = created.path("llmConfigurationId").asLong();
        var input = edit(created);
        input.put("model", "unsaved-synthetic");
        when(probe.test(any(), anyString())).thenReturn(LlmConnectionProbe.Outcome.CONNECTED);
        var tested = json(
                post(
                        "/api/llm-connection-tests",
                        ADMIN,
                        Map.of("configurationId", id, "expectedRevision", 0, "configuration", input)),
                201);
        assertThat(tested.path("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(tested.path("testedValues").path("model").asString()).isEqualTo("unsaved-synthetic");
        assertThat(get("/api/llm-configurations/" + id, ADMIN).path("model").asString())
                .isEqualTo("synthetic-v1");
        assertThat(get("/api/llm-configurations/" + id + "/history", ADMIN)
                        .path("totalElements")
                        .asInt())
                .isEqualTo(1);
        assertThat(tested.toString()).doesNotContain("synthetic-model-key-for-tests");
        input.put("apiKeyEnv", "OPENAI_API_KEY");
        var missing = json(post("/api/llm-connection-tests", ADMIN, Map.of("configuration", input)), 201);
        assertThat(missing.path("status").asString()).isEqualTo("FAILED");
        assertThat(missing.path("outcome").asString()).isEqualTo("KEY_NOT_CONFIGURED");
        input.put("apiKeyEnv", "OPENROUTER_API_KEY");
        when(probe.test(any(), anyString())).thenReturn(LlmConnectionProbe.Outcome.TIMEOUT);
        var timeout = json(post("/api/llm-connection-tests", ADMIN, Map.of("configuration", input)), 201);
        assertThat(timeout.path("outcome").asString()).isEqualTo("TIMEOUT");
        input.put("model", "synthetic-v2");
        json(put("/api/llm-configurations/" + id, ADMIN, input), 200);
        assertThat(get("/api/llm-connection-tests/" + tested.path("testId").asLong(), ADMIN)
                        .path("stale")
                        .asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("관리자 외 조회·시험·복원을 차단하고 위험 주소·임의 키 참조·누락값을 거부한다")
    void authorizationAndValidation() {
        for (var role : List.of(MemberRole.APPLICANT, MemberRole.INFRA, MemberRole.SECURITY)) {
            var actor = auth(
                    role == MemberRole.APPLICANT ? "applicant" : role.name().toLowerCase(), role);
            for (String path : List.of(
                    "/api/llm-configurations",
                    "/api/llm-configurations/management",
                    "/api/llm-configurations/1",
                    "/api/llm-configurations/1/history",
                    "/api/llm-connection-tests/1"))
                assertThat(mvc.get().uri(path).with(authentication(actor))).hasStatus(403);
            json(post("/api/llm-connection-tests", actor, Map.of("configuration", command("denied", false))), 403);
            json(
                    post(
                            "/api/llm-configurations/1/restorations",
                            actor,
                            Map.of("historyId", 1, "expectedRevision", 0, "reason", "합성")),
                    403);
        }
        var invalid = mapper.valueToTree(command("invalid", false));
        for (String url : List.of(
                "https://unregistered.example",
                "https://key@openrouter.ai/api",
                "https://openrouter.ai/api?key=synthetic",
                "http://169.254.169.254")) {
            ((ObjectNode) invalid).put("baseUrl", url);
            json(post("/api/llm-configurations/comparisons", ADMIN, invalid), 422);
        }
        ((ObjectNode) invalid).put("baseUrl", "https://openrouter.ai/api");
        ((ObjectNode) invalid).put("apiKeyEnv", "POSTGRES_PASSWORD");
        json(post("/api/llm-connection-tests", ADMIN, Map.of("configuration", invalid)), 422);
        assertThat(get("/api/llm-configurations/management", ADMIN).path("mode").asString())
                .isEqualTo("FAKE");
    }

    private Map<String, Object> command(String name, boolean active) {
        return Map.of(
                "name",
                name,
                "provider",
                "OPENAI_COMPATIBLE",
                "baseUrl",
                "https://openrouter.ai/api",
                "model",
                "synthetic-v1",
                "apiKeyEnv",
                "OPENROUTER_API_KEY",
                "purposes",
                List.of("INTERVIEW"),
                "priority",
                100,
                "active",
                active);
    }

    private ObjectNode edit(JsonNode created) {
        var node = (ObjectNode) created.deepCopy();
        node.set("expectedRevision", node.get("revision"));
        node.remove(List.of("revision", "llmConfigurationId", "keyConfigured"));
        return node;
    }

    private JsonNode get(String path, Authentication actor) {
        return json(mvc.get().uri(path).with(authentication(actor)).exchange(), 200);
    }

    private MvcTestResult post(String path, Authentication actor, Object body) {
        return mvc.post()
                .uri(path)
                .with(authentication(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))
                .exchange();
    }

    private MvcTestResult put(String path, Authentication actor, Object body) {
        return mvc.put()
                .uri(path)
                .with(authentication(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))
                .exchange();
    }

    private JsonNode json(MvcTestResult result, int status) {
        assertThat(result).hasStatus(status);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static Authentication auth(String username, MemberRole role) {
        return new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(username, username, role),
                null,
                List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
