package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmProvider;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.repository.LlmConfigurationRepository;
import kr.securityadvisor.llm.service.LlmConfigurationSelector;
import kr.securityadvisor.llm.service.LlmCredentialService;
import kr.securityadvisor.llm.service.LlmProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LlmConfigurationSelectorTest {
    @Test
    void 설치_허용목록에서_제외된_기존_구성은_호출하지_않고_다음_후보를_선택한다() {
        var repository = mock(LlmConfigurationRepository.class);
        var keys = mock(LlmCredentialService.class);
        when(keys.configured("OPENAI_API_KEY")).thenReturn(true);
        when(keys.key("OPENAI_API_KEY")).thenReturn("synthetic-key");
        var selector = new LlmConfigurationSelector(
                repository, new LlmProperties("live", "allowed.test"), keys, new JsonCodec(new ObjectMapper()));
        var old = candidate("removed.test");
        var valid = candidate("allowed.test");
        when(repository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(old, valid));
        assertThat(selector.select(LlmPurpose.INTERVIEW).configuration()).isSameAs(valid);
        when(repository.findByActiveTrueOrderByPriorityAscIdAsc()).thenReturn(List.of(old));
        assertThatThrownBy(() -> selector.select(LlmPurpose.INTERVIEW)).isInstanceOf(ServiceUnavailableException.class);
    }

    private LlmConfiguration candidate(String host) {
        return LlmConfiguration.create(
                "합성",
                LlmProvider.OPENAI_COMPATIBLE,
                "https://" + host,
                "synthetic",
                "OPENAI_API_KEY",
                "[\"INTERVIEW\"]",
                true,
                new BigDecimal("0.2"),
                1024,
                30,
                0,
                Set.of(host));
    }
}
