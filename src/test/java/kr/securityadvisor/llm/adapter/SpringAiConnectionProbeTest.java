package kr.securityadvisor.llm.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmProvider;
import kr.securityadvisor.llm.service.LlmConnectionProbe.Outcome;
import org.junit.jupiter.api.Test;

class SpringAiConnectionProbeTest {
    @Test
    void 기존_주소는_v1을_연결하고_명시한_사용자_경로는_유지한다() {
        assertThat(OpenAiEndpoint.baseUrl("https://openrouter.ai/api/")).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(OpenAiEndpoint.baseUrl("https://api.openai.com")).isEqualTo("https://api.openai.com/v1");
        assertThat(OpenAiEndpoint.baseUrl("http://ollama:11434")).isEqualTo("http://ollama:11434/v1");
        assertThat(OpenAiEndpoint.baseUrl("https://openrouter.ai/api/v1")).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(OpenAiEndpoint.baseUrl("https://example.test/custom/v2"))
                .isEqualTo("https://example.test/custom/v2");
    }

    @Test
    void 실제_SDK_경로와_합성_요청_한도_인증실패_빈응답을_검사한다() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var status = new AtomicInteger(200);
        var calls = new AtomicInteger();
        var content = new java.util.concurrent.atomic.AtomicReference<>("연결 확인");
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("합성 연결 시험", "512").doesNotContain("실제 신청 자료");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-test-key");
            String response = status.get() == 200
                    ? "{\"id\":\"synthetic\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"synthetic\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                            + content.get() + "\"},\"finish_reason\":\"stop\"}]}"
                    : "{\"error\":{\"message\":\"synthetic authentication failure\",\"type\":\"invalid_request_error\"}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var configuration = LlmConfiguration.create(
                    "합성",
                    LlmProvider.OPENAI_COMPATIBLE,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "synthetic",
                    "OPENAI_API_KEY",
                    "[\"INTERVIEW\"]",
                    true,
                    new BigDecimal("0.2"),
                    2048,
                    5,
                    1,
                    Set.of("127.0.0.1"));
            var probe = new SpringAiConnectionProbe();
            assertThat(probe.test(configuration, "synthetic-test-key")).isEqualTo(Outcome.CONNECTED);
            status.set(401);
            assertThat(probe.test(configuration, "synthetic-test-key")).isEqualTo(Outcome.AUTHENTICATION_FAILED);
            status.set(500);
            assertThat(probe.test(configuration, "synthetic-test-key")).isEqualTo(Outcome.UNAVAILABLE);
            status.set(200);
            content.set("");
            assertThat(probe.test(configuration, "synthetic-test-key")).isEqualTo(Outcome.EMPTY_RESPONSE);
            assertThat(calls.get()).isEqualTo(4);
        } finally {
            server.stop(0);
        }
    }
}
