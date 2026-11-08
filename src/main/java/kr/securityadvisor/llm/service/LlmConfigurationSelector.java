package kr.securityadvisor.llm.service;

import java.util.List;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.repository.LlmConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 용도에 맞는 활성 구성 중 priority 순으로, 키 환경변수가 설정된 첫 구성을 고른다. 키 값은 반환 직전에만 환경에서 읽는다. */
@Component
@RequiredArgsConstructor
public class LlmConfigurationSelector {

    private final LlmConfigurationRepository repository;
    private final LlmProperties properties;
    private final Environment environment;
    private final JsonCodec json;

    /** 선택된 구성과 그 키. */
    public record Selected(LlmConfiguration configuration, String apiKey) {}

    @Transactional(readOnly = true)
    public Selected select(LlmPurpose purpose) {
        List<LlmConfiguration> candidates = repository.findByActiveTrueOrderByPriorityAscIdAsc();
        for (LlmConfiguration configuration : candidates) {
            if (!json.readStrings(configuration.getPurposes()).contains(purpose.name())) {
                continue;
            }
            String key = environment.getProperty(configuration.getApiKeyEnv());
            if (key == null || key.isBlank()) {
                continue;
            }
            configuration.requireAllowedHost(properties.allowedHostSet());
            return new Selected(configuration, key);
        }
        throw new ServiceUnavailableException("용도 " + purpose + " 에 쓸 수 있는 LLM 구성이 없습니다(활성 구성의 키 환경변수가 설정되어 있는지 확인)");
    }
}
