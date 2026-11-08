package kr.securityadvisor.llm.service;

import kr.securityadvisor.llm.domain.LlmPurpose;

/**
 * 업무 코드가 보는 유일한 LLM 진입점(공통 어댑터 인터페이스). Spring AI 타입은 {@code llm.adapter} 밖으로 나오지 않는다.
 * 구현은 DB 구성(llm_configurations)으로 용도별 모델을 고른다.
 */
public interface LlmGateway {

    /**
     * @throws ServiceUnavailableException 쓸 수 있는 구성이 없거나 호출이 실패했을 때(503)
     */
    LlmResult complete(LlmPurpose purpose, LlmPrompt prompt);
}
