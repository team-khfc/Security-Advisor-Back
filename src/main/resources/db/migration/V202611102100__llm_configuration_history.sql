-- 기존 모델 구성은 변경하지 않고 현재 값을 최초 이력으로 보존한다. 복원도 새 이력을 추가한다.
CREATE TABLE llm_configuration_histories (
    llm_configuration_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    llm_configuration_id BIGINT NOT NULL,
    configuration_revision BIGINT NOT NULL,
    snapshot JSONB NOT NULL,
    action VARCHAR(20) NOT NULL,
    changed_by VARCHAR(50) NOT NULL,
    reason VARCHAR(500),
    restored_from_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(llm_configuration_id, configuration_revision)
);
INSERT INTO llm_configuration_histories(llm_configuration_id, configuration_revision, snapshot, action, changed_by)
SELECT llm_configuration_id, revision, jsonb_build_object(
    'name', name, 'provider', provider, 'baseUrl', base_url, 'model', model, 'apiKeyEnv', api_key_env,
    'purposes', purposes, 'temperature', temperature, 'maxTokens', max_tokens,
    'timeoutSeconds', timeout_seconds, 'priority', priority, 'active', active
), 'BASELINE', 'system' FROM llm_configurations;
CREATE INDEX idx_llm_history_configuration ON llm_configuration_histories(llm_configuration_id, llm_configuration_history_id DESC);
CREATE FUNCTION reject_llm_history_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'LLM 구성 이력은 추가 전용입니다';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_llm_history_append_only BEFORE UPDATE OR DELETE ON llm_configuration_histories
FOR EACH ROW EXECUTE FUNCTION reject_llm_history_change();

CREATE TABLE llm_connection_tests (
    llm_connection_test_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    llm_configuration_id BIGINT,
    configuration_revision BIGINT,
    snapshot JSONB NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    elapsed_millis BIGINT NOT NULL,
    tested_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
