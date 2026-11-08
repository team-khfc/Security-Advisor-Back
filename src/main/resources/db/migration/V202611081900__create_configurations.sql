-- F-08 기관 설정 초안. 편집은 초안에만, 게시(POST /api/configurations/{id}/publications)하면 publications 에 새 버전이 생긴다.
-- 게시본은 새 사업 건에만 적용되고 기존 사업 건은 생성 시점 버전을 유지한다(불변식 8). 초안은 설치당 1건.
CREATE TABLE configurations
(
    configuration_id   BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    draft_field_schema JSONB       NOT NULL,
    based_on_version   INTEGER     NOT NULL,
    note               VARCHAR(500),
    updated_by         VARCHAR(50) NOT NULL,
    revision           BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE configurations IS '기관 설정 초안(설치당 1건). 저장 키·범위·적용 서류 유형은 데이터 계약 — 게시 시 검증';
