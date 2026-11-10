-- 게시 당시 메타데이터는 이후 초안 수정과 독립적으로 보존한다.
ALTER TABLE publications ADD COLUMN published_by VARCHAR(50);
ALTER TABLE publications ADD COLUMN note VARCHAR(500);
ALTER TABLE publications ADD COLUMN previous_version INTEGER;

-- 조회 API에서 초안을 생성하지 않는다. 최초 설치·기존 설치 모두 현재 게시본으로 시작한다.
INSERT INTO configurations (draft_field_schema, based_on_version, updated_by)
SELECT field_schema, version, 'system' FROM publications
WHERE NOT EXISTS (SELECT 1 FROM configurations)
ORDER BY version DESC LIMIT 1;
CREATE UNIQUE INDEX uq_configurations_singleton ON configurations ((true));
