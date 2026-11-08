-- F-10 LLM 구성: 공급자·엔드포인트·모델·용도를 DB 로 관리. API 키는 환경변수 이름만(불변 원칙: 비밀값 DB 저장 금지).
-- base_url 호스트는 애플리케이션 허용 목록(securityadvisor.llm.allowed-hosts)에 있어야 등록·호출된다(SSRF 방지).
CREATE TABLE llm_configurations
(
    llm_configuration_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL UNIQUE,
    provider             VARCHAR(30)  NOT NULL CHECK (provider IN ('OPENAI_COMPATIBLE')),
    base_url             VARCHAR(300) NOT NULL,
    model                VARCHAR(100) NOT NULL,
    api_key_env          VARCHAR(100) NOT NULL,
    purposes             JSONB        NOT NULL,
    temperature          NUMERIC(3,2) NOT NULL DEFAULT 0.2,
    max_tokens           INTEGER      NOT NULL DEFAULT 1024,
    timeout_seconds      INTEGER      NOT NULL DEFAULT 60,
    priority             INTEGER      NOT NULL DEFAULT 100,
    active               BOOLEAN      NOT NULL DEFAULT true,
    revision             BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE llm_configurations IS 'LLM 구성. 용도별로 priority 가 낮은 활성 구성 중 키 환경변수가 설정된 첫 구성을 쓴다';
COMMENT ON COLUMN llm_configurations.api_key_env IS '환경변수 이름. 값은 저장하지 않는다';

-- seed: 상용 1종(OpenAI) + 공개 모델 1종(Ollama 의 OpenAI 호환 API, EXAONE). 둘 다 활성이며 키 환경변수가 있는 쪽이 쓰인다.
INSERT INTO llm_configurations (name, provider, base_url, model, api_key_env, purposes, temperature, max_tokens, timeout_seconds, priority) VALUES
('openai-default', 'OPENAI_COMPATIBLE', 'https://api.openai.com', 'gpt-4o-mini', 'OPENAI_API_KEY',
 '["INTERVIEW","SUMMARY","DOCUMENT","INQUIRY"]', 0.2, 1024, 60, 10),
('ollama-exaone', 'OPENAI_COMPATIBLE', 'http://ollama:11434/v1', 'exaone3.5:7.8b', 'OLLAMA_API_KEY',
 '["INTERVIEW","SUMMARY","DOCUMENT","INQUIRY"]', 0.2, 1024, 120, 20);
