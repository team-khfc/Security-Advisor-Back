package kr.securityadvisor.llm.domain;

/** 공급자 종류. 1차 범위는 OpenAI 호환 API 하나로 상용(OpenAI)·공개 모델(Ollama /v1, vLLM, Friendli 등)을 모두 다룬다. */
public enum LlmProvider {
    OPENAI_COMPATIBLE
}
