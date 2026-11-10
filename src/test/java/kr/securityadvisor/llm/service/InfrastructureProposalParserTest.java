package kr.securityadvisor.llm.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InfrastructureProposalParserTest {
    private final InfrastructureProposalParser parser = new InfrastructureProposalParser(new ObjectMapper());

    @Test
    @DisplayName("첨부 원문 인용은 유지하고 자료에 없는 포트·위조 인용은 확인 필요로 비운다")
    void groundsEachField() {
        var sources = List.of(new InquiryContextService.Source("U1", "합성 첨부 원문", "합성 웹 192.0.2.10에서 합성 API로 연결한다."));
        var rows = parser.parse("""
                {"connections":[{
                  "sourceSystem":{"value":"합성 웹","sourceTag":"U1","quote":"합성 웹 192.0.2.10에서 합성 API로 연결한다."},
                  "sourceAddress":{"value":"192.0.2.10","sourceTag":"U1","quote":"합성 웹 192.0.2.10에서 합성 API로 연결한다."},
                  "port":{"value":"443","sourceTag":"U1","quote":"포트 443으로 연결한다."},
                  "destinationAddress":{"value":"198.51.100.10","sourceTag":"U99","quote":"198.51.100.10"}
                }]}
                """, sources);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().fields().get("sourceAddress").value()).isEqualTo("192.0.2.10");
        assertThat(rows.getFirst().fields().get("port").value()).isEmpty();
        assertThat(rows.getFirst().fields().get("destinationAddress").value()).isEmpty();
        assertThat(rows.getFirst().fields().get("endDate").value()).isEmpty();
    }

    @Test
    @DisplayName("비정상 모델 출력은 성공으로 가장하지 않는다")
    void malformedOutputFails() {
        assertThatThrownBy(() -> parser.parse("응답 형식 오류", List.of())).isInstanceOf(ServiceUnavailableException.class);
        assertThat(parser.parse("{\"connections\":[]}", List.of())).isEmpty();
    }
}
