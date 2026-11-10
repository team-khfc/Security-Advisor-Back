package kr.securityadvisor.llm.adapter;

import static org.assertj.core.api.Assertions.*;

import kr.securityadvisor.common.error.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SpringAiOutputLimitTest {
    @ParameterizedTest
    @ValueSource(strings = {"length", "max_tokens"})
    @DisplayName("JSON이 닫혀 있어도 출력 한도로 중단된 응답은 완성본으로 반환하지 않는다")
    void truncated(String reason) {
        assertThatThrownBy(() -> SpringAiLlmGateway.validatedResult("{\"items\":[]}", reason, "test/model", 1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("출력 한도");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("추론이 토큰을 모두 사용해 본문이 없어도 빈 응답 대신 출력 한도를 안내한다")
    void reasoningConsumedBudget(String text) {
        assertThatThrownBy(() -> SpringAiLlmGateway.validatedResult(text, "length", "test/model", 1L))
                .hasMessageContaining("추론 토큰");
    }

    @Test
    @DisplayName("정상 종료한 응답은 모델 출처를 유지한다")
    void completed() {
        var result = SpringAiLlmGateway.validatedResult("완성된 응답", "stop", "test/model", 1L);
        assertThat(result.text()).isEqualTo("완성된 응답");
        assertThat(result.configurationId()).isEqualTo(1L);
    }
}
