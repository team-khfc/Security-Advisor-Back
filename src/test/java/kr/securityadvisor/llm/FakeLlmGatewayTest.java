package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.securityadvisor.llm.adapter.FakeLlmGateway;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.service.LlmPrompt;
import kr.securityadvisor.llm.service.LlmResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** fake 모드 응답 형식: 지식 조각이 있을 때만 항목별 제안 JSON 을 만든다. */
class FakeLlmGatewayTest {

    private final FakeLlmGateway gateway = new FakeLlmGateway();

    @Test
    @DisplayName("지식 조각이 있으면 항목 키마다 제안하고 첫 조각을 근거로 단다, 없으면 제안하지 않는다")
    void proposals() {
        String user = "[{\"fieldKey\":\"firewall.purpose\"},{\"fieldKey\":\"firewall.period\"}]";
        LlmResult withKnowledge =
                gateway.complete(LlmPurpose.INTERVIEW, LlmPrompt.of("sys", List.of("[S3] 출처 — 본문"), List.of(), user));
        assertThat(withKnowledge.text())
                .contains("\"fieldKey\":\"firewall.purpose\"")
                .contains("\"evidence\":[\"S3\"]");
        assertThat(withKnowledge.modelReference()).isEqualTo("fake/offline");

        LlmResult without = gateway.complete(LlmPurpose.INTERVIEW, LlmPrompt.of("sys", List.of(), List.of(), user));
        assertThat(without.text()).isEqualTo("{\"proposals\":[]}");
    }
}
