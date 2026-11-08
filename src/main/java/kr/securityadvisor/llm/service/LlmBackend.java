package kr.securityadvisor.llm.service;

import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.llm.domain.LlmPurpose;

/**
 * 실제 모델을 부르는 쪽(llm.adapter 의 Spring AI 구현·fake 구현)이 구현하는 내부 인터페이스. 업무 코드는 이것을 쓰지 않고
 * {@link LlmGateway}(감사 기록을 감싼 진입점)만 쓴다.
 */
public interface LlmBackend {

    /**
     * @throws ServiceUnavailableException 쓸 수 있는 구성이 없거나 호출이 실패했을 때(503)
     */
    LlmResult complete(LlmPurpose purpose, LlmPrompt prompt);
}
