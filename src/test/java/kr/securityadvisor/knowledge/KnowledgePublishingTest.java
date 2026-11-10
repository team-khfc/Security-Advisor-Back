package kr.securityadvisor.knowledge;

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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Tag("docker")
@SpringBootTest(
        properties = {
            "spring.application.name=knowledge-publishing-test",
            "securityadvisor.demo.account-password=Test-Demo-1234",
            "securityadvisor.llm.mode=fake"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnowledgePublishingTest {
    @Autowired
    MockMvcTester mvc;

    @Autowired
    ObjectMapper mapper;

    private static final Authentication ADMIN = auth("admin", MemberRole.ADMIN);
    private static final Authentication OWNER = auth("applicant", MemberRole.APPLICANT);
    private static final Authentication INFRA = auth("infra", MemberRole.INFRA);

    @Test
    @DisplayName("초안은 조회에 노출되지 않고 게시 후 신규 사업만 변경되며 기존 사업·추가 신청과 원문 이력은 유지된다")
    void publicationPinsKnowledgeAndCurrentAccess() throws Exception {
        JsonNode oldCase = json(
                post(
                        "/api/business-cases",
                        OWNER,
                        Map.of("title", "합성 지식 기존 사업", "summary", "합성", "docTypes", List.of("FIREWALL"))),
                201);
        long oldPublication = oldCase.path("knowledgePublicationId").asLong();
        JsonNode oldSnapshot = get("/api/knowledge-publications/" + oldPublication, ADMIN);
        ObjectNode snippet = (ObjectNode)
                get("/api/knowledge-drafts/current/snippets/1", ADMIN).deepCopy();
        String oldBody = snippet.path("body").asString();
        snippet.put("body", oldBody + " 합성검증검색단어: v2에서 추가한 지식입니다.");
        snippet.put("sourceVersion", "합성 v2");
        snippet.set("expectedRevision", snippet.get("revision"));
        json(put("/api/knowledge-snippets/1", ADMIN, snippet), 200);
        ObjectNode context = (ObjectNode)
                get("/api/knowledge-drafts/current/private-contexts/1", ADMIN).deepCopy();
        String oldIp = context.path("attributes").path("ip").asString();
        ((ObjectNode) context.get("attributes")).put("ip", "192.0.2.222");
        context.set("expectedRevision", context.get("revision"));
        json(put("/api/private-contexts/1", ADMIN, context), 200);
        assertThat(get("/api/knowledge-snippets/search?q=합성검증검색단어", OWNER).isEmpty())
                .isTrue();
        assertThat(get("/api/private-contexts/suggestions?q=WAS-01", INFRA)
                        .get(0)
                        .path("attributes")
                        .path("ip")
                        .asString())
                .isEqualTo(oldIp);
        JsonNode comparison = get("/api/knowledge-drafts/current/comparison", ADMIN);
        assertThat(comparison.path("changes").size()).isGreaterThanOrEqualTo(2);
        JsonNode published = json(
                post(
                        "/api/knowledge-drafts/current/publications",
                        ADMIN,
                        Map.of("expectedRevision", comparison.path("revision").asLong())),
                201);
        long newPublication = published.path("knowledgePublicationId").asLong();
        assertThat(newPublication).isNotEqualTo(oldPublication);
        JsonNode newCase = json(
                post(
                        "/api/business-cases",
                        OWNER,
                        Map.of("title", "합성 지식 신규 사업", "summary", "합성", "docTypes", List.of("FIREWALL"))),
                201);
        assertThat(newCase.path("knowledgePublicationId").asLong()).isEqualTo(newPublication);
        assertThat(get("/api/business-cases/" + oldCase.path("businessCaseId").asLong(), OWNER)
                        .path("knowledgePublicationId")
                        .asLong())
                .isEqualTo(oldPublication);
        json(
                post(
                        "/api/business-cases/" + oldCase.path("businessCaseId").asLong() + "/security-requests",
                        OWNER,
                        Map.of("docType", "SECURITY_REVIEW")),
                201);
        assertThat(get("/api/knowledge-snippets/search?q=합성검증검색단어&knowledgePublicationId=" + oldPublication, OWNER)
                        .isEmpty())
                .isTrue();
        assertThat(get("/api/knowledge-snippets/search?q=합성검증검색단어&knowledgePublicationId=" + newPublication, OWNER)
                        .size())
                .isEqualTo(1);
        assertThat(get("/api/private-contexts/suggestions?q=WAS-01&knowledgePublicationId=" + oldPublication, INFRA)
                        .get(0)
                        .path("attributes")
                        .path("ip")
                        .asString())
                .isEqualTo(oldIp);
        assertThat(get("/api/private-contexts/suggestions?q=WAS-01&knowledgePublicationId=" + newPublication, INFRA)
                        .get(0)
                        .path("attributes")
                        .path("ip")
                        .asString())
                .isEqualTo("192.0.2.222");
        assertThat(get("/api/knowledge-publications/" + oldPublication, ADMIN)).isEqualTo(oldSnapshot);
        assertThat(get("/api/knowledge-publications?page=0&size=1", ADMIN)
                        .path("content")
                        .get(0)
                        .path("knowledgePublicationId")
                        .asLong())
                .isEqualTo(newPublication);
        assertThat(get("/api/knowledge-publications?page=1&size=1", ADMIN)
                        .path("content")
                        .get(0)
                        .path("knowledgePublicationId")
                        .asLong())
                .isEqualTo(oldPublication);
        JsonNode frozenComparison = get("/api/knowledge-publications/" + newPublication + "/comparison", ADMIN);
        context = (ObjectNode)
                get("/api/knowledge-drafts/current/private-contexts/1", ADMIN).deepCopy();
        context.putArray("visibleRoles").add("SECURITY");
        context.set("expectedRevision", context.get("revision"));
        json(put("/api/private-contexts/1", ADMIN, context), 200);
        publishCurrent();
        assertThat(get("/api/private-contexts/suggestions?q=WAS-01&knowledgePublicationId=" + oldPublication, INFRA)
                        .isEmpty())
                .isTrue();
        assertThat(get("/api/knowledge-publications/" + newPublication + "/comparison", ADMIN))
                .isEqualTo(frozenComparison);
        assertThat(get("/api/audit-events?targetType=PRIVATE_CONTEXT&size=100", ADMIN)
                        .toString())
                .doesNotContain("192.0.2.222", "합성 업무서버 01");
    }

    @Test
    @DisplayName("실제 동시 저장은 하나만 성공하며 비교 후 다른 저장이 있으면 게시를 차단한다")
    void concurrentSaveAndStalePublish() throws Exception {
        ObjectNode current = (ObjectNode) get("/api/knowledge-drafts/current/snippets/2", ADMIN);
        ObjectNode first = current.deepCopy().put("sourceVersion", "동시 수정 가");
        first.set("expectedRevision", current.get("revision"));
        ObjectNode second = current.deepCopy().put("sourceVersion", "동시 수정 나");
        second.set("expectedRevision", current.get("revision"));
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> {
                ready.countDown();
                start.await();
                return put("/api/knowledge-snippets/2", ADMIN, first);
            });
            var b = executor.submit(() -> {
                ready.countDown();
                start.await();
                return put("/api/knowledge-snippets/2", ADMIN, second);
            });
            ready.await();
            start.countDown();
            var results = List.of(a.get(), b.get());
            assertThat(results.stream().map(r -> r.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(200, 409);
            JsonNode saved = get("/api/knowledge-drafts/current/snippets/2", ADMIN);
            assertThat(saved.path("revision").asLong())
                    .isEqualTo(current.path("revision").asLong() + 1);
        }
        JsonNode comparison = get("/api/knowledge-drafts/current/comparison", ADMIN);
        json(
                put(
                        "/api/knowledge-drafts/current",
                        ADMIN,
                        Map.of("expectedRevision", comparison.path("revision").asLong(), "note", "합성 메모")),
                200);
        json(
                post(
                        "/api/knowledge-drafts/current/publications",
                        ADMIN,
                        Map.of("expectedRevision", comparison.path("revision").asLong())),
                409);
        publishCurrent();
        json(
                post(
                        "/api/knowledge-drafts/current/publications",
                        ADMIN,
                        Map.of(
                                "expectedRevision",
                                get("/api/knowledge-drafts/current", ADMIN)
                                        .path("revision")
                                        .asLong())),
                422);
    }

    @Test
    @DisplayName("다른 역할은 초안·비공개 이력·지표를 읽거나 게시할 수 없으며 잘못된 입력은 저장되지 않는다")
    void permissionsAndValidation() throws Exception {
        for (Authentication actor : List.of(OWNER, INFRA, auth("security", MemberRole.SECURITY))) {
            for (String path : List.of(
                    "/api/knowledge-drafts/current",
                    "/api/knowledge-drafts/current/comparison",
                    "/api/knowledge-drafts/current/snippets",
                    "/api/knowledge-drafts/current/private-contexts",
                    "/api/knowledge-publications",
                    "/api/knowledge-publications/1",
                    "/api/knowledge-publications/1/comparison",
                    "/api/insights/summary"))
                assertThat(mvc.get()
                                .uri(path)
                                .with(authentication(actor))
                                .exchange()
                                .getResponse()
                                .getStatus())
                        .isEqualTo(403);
            json(post("/api/knowledge-drafts/current/publications", actor, Map.of("expectedRevision", 0)), 403);
        }
        long revision =
                get("/api/knowledge-drafts/current", ADMIN).path("revision").asLong();
        json(
                post(
                        "/api/knowledge-snippets",
                        ADMIN,
                        Map.of(
                                "sourceTitle",
                                "합성",
                                "sourceVersion",
                                "1",
                                "sourceLocator",
                                "1절",
                                "fieldKeys",
                                List.of("missing.field"),
                                "docTypes",
                                List.of(),
                                "body",
                                "합성")),
                422);
        json(
                post(
                        "/api/private-contexts",
                        ADMIN,
                        Map.of(
                                "kind",
                                "SERVER",
                                "name",
                                "합성",
                                "visibleRoles",
                                List.of("APPLICANT"),
                                "attributes",
                                Map.of("ip", "192.0.2.1"))),
                422);
        json(
                post(
                        "/api/private-contexts",
                        ADMIN,
                        Map.of(
                                "kind",
                                "SERVER",
                                "name",
                                "합성",
                                "visibleRoles",
                                List.of("INFRA"),
                                "attributes",
                                Map.of())),
                422);
        assertThat(get("/api/knowledge-drafts/current", ADMIN).path("revision").asLong())
                .isEqualTo(revision);
        assertThat(mvc.get()
                        .uri("/api/knowledge-publications?size=101")
                        .with(authentication(ADMIN))
                        .exchange()
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
    }

    private void publishCurrent() throws Exception {
        json(
                post(
                        "/api/knowledge-drafts/current/publications",
                        ADMIN,
                        Map.of(
                                "expectedRevision",
                                get("/api/knowledge-drafts/current", ADMIN)
                                        .path("revision")
                                        .asLong())),
                201);
    }

    private JsonNode get(String path, Authentication actor) throws Exception {
        return json(mvc.get().uri(path).with(authentication(actor)).exchange(), 200);
    }

    private MvcTestResult post(String path, Authentication actor, Object value) {
        return mvc.post()
                .uri(path)
                .with(authentication(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(value))
                .exchange();
    }

    private MvcTestResult put(String path, Authentication actor, Object value) {
        return mvc.put()
                .uri(path)
                .with(authentication(actor))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(value))
                .exchange();
    }

    private JsonNode json(MvcTestResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(status);
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static Authentication auth(String username, MemberRole role) {
        return new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(username, "합성", role), null, List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
