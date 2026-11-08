-- OpenRouter(OpenAI 호환) 경유 DeepSeek V4.1 Flash 1017 구성 seed. 키는 환경변수 OPENROUTER_API_KEY 로만 받는다(값 저장 금지).
-- priority 5 → OPENROUTER_API_KEY 가 설정돼 있으면 openai-default(10)·ollama-exaone(20)보다 먼저 선택된다.
INSERT INTO llm_configurations (name, provider, base_url, model, api_key_env, purposes, temperature, max_tokens, timeout_seconds, priority)
VALUES ('openrouter-deepseek-v4.1-flash-1017', 'OPENAI_COMPATIBLE', 'https://openrouter.ai/api/v1',
        'deepseek/deepseek-v4.1-flash-1017', 'OPENROUTER_API_KEY',
        '["INTERVIEW","SUMMARY","DOCUMENT","INQUIRY"]', 0.2, 1024, 90, 5);
