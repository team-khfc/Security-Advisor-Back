package kr.securityadvisor.llm.service;

import java.util.Arrays;
import java.util.List;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 모델 키 전용 참조만 허용해 임의의 서버 환경변수가 외부 인증 헤더로 나가는 것을 막는다. */
@Component
@RequiredArgsConstructor
public class LlmCredentialService {
    private final Environment environment;

    /** 설치에서 모델 키로 허용한 환경변수 이름만 제공한다. */
    public List<String> allowedReferences() {
        return Arrays.stream(environment
                        .getProperty(
                                "securityadvisor.llm.allowed-key-envs",
                                "OPENAI_API_KEY,OPENROUTER_API_KEY,OLLAMA_API_KEY")
                        .split(","))
                .map(String::strip)
                .filter(s -> s.matches("[A-Z][A-Z0-9_]{2,99}"))
                .distinct()
                .sorted()
                .toList();
    }

    /** 임의의 서버 비밀을 모델 키로 참조하는 것을 차단한다. */
    public void requireAllowed(String reference) {
        if (!allowedReferences().contains(reference))
            throw new DomainRuleViolationException("설치 설정에서 허용한 모델 키 참조를 선택해 주세요");
    }

    /** 키 값 대신 허용된 참조의 설정 여부만 확인한다. */
    public boolean configured(String reference) {
        return allowedReferences().contains(reference) && !key(reference).isBlank();
    }

    /** 키 값은 호출 어댑터에만 전달하며 DTO·로그에 넣지 않는다. */
    public String key(String reference) {
        requireAllowed(reference);
        return environment.getProperty(reference, "");
    }
}
