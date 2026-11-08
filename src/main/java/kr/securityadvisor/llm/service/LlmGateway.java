package kr.securityadvisor.llm.service;

import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.llm.domain.LlmPurpose;

/**
 * 업무 코드가 보는 유일한 LLM 진입점(공통 어댑터 인터페이스). Spring AI 타입은 {@code llm.adapter} 밖으로 나오지 않는다.
 * 구현은 DB 구성(llm_configurations)으로 용도별 모델을 고르고, 호출마다 감사 이력(구성·용도·대상·성공/실패·소요 시간)을 남긴다.
 */
public interface LlmGateway {

    /**
     * @param context 감사용 맥락(행위자·대상). null 이면 대상 없이 기록한다
     * @throws ServiceUnavailableException 쓸 수 있는 구성이 없거나 호출이 실패했을 때(503)
     */
    LlmResult complete(LlmPurpose purpose, LlmPrompt prompt, LlmCallContext context);

    default LlmResult complete(LlmPurpose purpose, LlmPrompt prompt) {
        return complete(purpose, prompt, null);
    }
}
