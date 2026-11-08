-- DeepSeek V4.1 Flash 는 추론(사고) 토큰이 응답 상한을 함께 쓰므로 1024 로는 본문이 비어 올 수 있다 → 4096
UPDATE llm_configurations SET max_tokens = 4096 WHERE name = 'openrouter-deepseek-v4.1-flash-1017';
