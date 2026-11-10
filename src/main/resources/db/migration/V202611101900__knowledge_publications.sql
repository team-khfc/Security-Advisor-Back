-- 지식 편집은 기존 행을 초안으로 사용하고 게시 시 불변 사본을 만든다.
CREATE TABLE knowledge_publications (
    knowledge_publication_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version INTEGER NOT NULL UNIQUE,
    snapshot JSONB NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    published_by VARCHAR(50),
    note VARCHAR(500),
    previous_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO knowledge_publications(version, snapshot, published_at, published_by)
SELECT 1, jsonb_build_object(
    'snippets', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'knowledgeSnippetId', knowledge_snippet_id, 'sourceTitle', source_title,
        'sourceVersion', source_version, 'sourceLocator', source_locator,
        'citation', source_title || ' ' || source_version || ' ' || source_locator,
        'fieldKeys', field_keys, 'docTypes', doc_types, 'body', body, 'active', active, 'revision', revision
    ) ORDER BY knowledge_snippet_id) FROM knowledge_snippets), '[]'::jsonb),
    'privateContexts', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'privateContextId', private_context_id, 'kind', kind, 'name', name, 'aliases', aliases,
        'attributes', attributes, 'visibleRoles', visible_roles, 'note', note,
        'active', active, 'revision', revision
    ) ORDER BY private_context_id) FROM private_contexts), '[]'::jsonb)
), now(), 'system';

CREATE TABLE knowledge_drafts (
    knowledge_draft_id BIGINT PRIMARY KEY CHECK (knowledge_draft_id = 1),
    based_on_version INTEGER NOT NULL,
    note VARCHAR(500),
    revision BIGINT NOT NULL DEFAULT 0,
    change_sequence BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO knowledge_drafts(knowledge_draft_id, based_on_version, updated_by) VALUES (1, 1, 'system');

ALTER TABLE business_cases ADD COLUMN knowledge_publication_id BIGINT NOT NULL DEFAULT 1;
COMMENT ON COLUMN business_cases.knowledge_publication_id IS '생성 시점 지식 게시본. 기존 사업과 추가 신청은 같은 버전을 유지한다';
