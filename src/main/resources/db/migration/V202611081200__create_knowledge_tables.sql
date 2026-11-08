-- F-09 지식 기반(1차 범위): 공개자료 스니펫(팀 큐레이션 + Postgres 전문검색) + 운영 기관 비공개 컨텍스트(구조화 레코드).
-- 벡터 색인은 쓰지 않는다(2026-11-08 결정, ADR 0006). 외래 키 제약은 두지 않는다(팀 결정).

-- 1) 공개자료 스니펫: 국정원·KISA 등 공개 문서에서 항목별로 발췌한 문장. 검색은 field_keys(사람 태깅) 1차 + FTS 순위.
CREATE TABLE knowledge_snippets
(
    knowledge_snippet_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_title         VARCHAR(200) NOT NULL,
    source_version       VARCHAR(50)  NOT NULL,
    source_locator       VARCHAR(200) NOT NULL,
    field_keys           JSONB        NOT NULL,
    doc_types            JSONB        NOT NULL,
    body                 TEXT         NOT NULL,
    active               BOOLEAN      NOT NULL DEFAULT true,
    revision             BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    search_vector        TSVECTOR     GENERATED ALWAYS AS (to_tsvector('simple', body)) STORED
);
CREATE INDEX idx_knowledge_snippets_fts ON knowledge_snippets USING GIN (search_vector);
CREATE INDEX idx_knowledge_snippets_field_keys ON knowledge_snippets USING GIN (field_keys);
COMMENT ON TABLE knowledge_snippets IS '공개자료 발췌(지식). AI 제안·담당자 질의의 근거. 출처·위치를 함께 표시한다';
COMMENT ON COLUMN knowledge_snippets.search_vector IS 'simple 구성(한국어 사전 없음) 전문검색 — 1차 필터는 field_keys';

-- 2) 운영 기관 비공개 컨텍스트: 서버·망·자산 레코드. AI 는 여기서 뽑아 준 사실만 말할 수 있다(원칙 3). 신청자에게는 보이지 않는다.
CREATE TABLE private_contexts
(
    private_context_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind               VARCHAR(30)  NOT NULL CHECK (kind IN ('SERVER', 'NETWORK_ZONE', 'ASSET', 'NOTE')),
    name               VARCHAR(200) NOT NULL,
    aliases            JSONB        NOT NULL,
    attributes         JSONB        NOT NULL,
    visible_roles      JSONB        NOT NULL,
    note               TEXT,
    active             BOOLEAN      NOT NULL DEFAULT true,
    revision           BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_private_contexts_name ON private_contexts (lower(name));
COMMENT ON TABLE private_contexts IS '기관 비공개 컨텍스트(사실). 전부 합성 데이터만. visible_roles 에 APPLICANT 를 넣지 않는다';

-- 3) seed — 전부 합성. 출처 제목·판본·위치도 실제 문서를 가리키지 않는 예시다(팀이 실제 공개자료 발췌로 교체한다).
INSERT INTO knowledge_snippets (source_title, source_version, source_locator, field_keys, doc_types, body) VALUES
('공공기관 네트워크 접근통제 가이드(합성 예시)', '2026 합성판', '3장 2절',
 '["firewall.period"]', '["FIREWALL"]',
 '방화벽 허용 규칙은 사용 기간을 명시한다. 종료일이 없는 상시 규칙은 정기 재검토일을 두고 재검토 시 필요성을 다시 확인한다.'),
('공공기관 네트워크 접근통제 가이드(합성 예시)', '2026 합성판', '3장 1절',
 '["firewall.purpose"]', '["FIREWALL"]',
 '허용 목적은 어떤 업무를 위해 어떤 시스템 간 통신이 필요한지 구체적으로 기술한다. "업무상 필요"처럼 포괄적인 사유는 반려 대상이다.'),
('공공기관 네트워크 접근통제 가이드(합성 예시)', '2026 합성판', '4장 1절',
 '["firewall.protocol","firewall.port"]', '["FIREWALL"]',
 '프로토콜과 포트는 서비스에 필요한 최소 범위로 지정한다. 전체 포트 허용(any)은 금지하며, 웹 서비스는 HTTPS(443)를 기본으로 한다.'),
('정보시스템 보안성 검토 안내(합성 예시)', '2026 합성판', '2장 3절',
 '["review.access_control"]', '["SECURITY_REVIEW"]',
 '접근 권한은 직무별 최소 권한으로 부여하고, 관리자 권한은 별도 계정으로 분리한다. 권한 부여·회수 절차와 주기적 검토를 기술한다.'),
('정보시스템 보안성 검토 안내(합성 예시)', '2026 합성판', '2장 4절',
 '["review.logging"]', '["SECURITY_REVIEW"]',
 '접속 기록과 처리 기록은 최소 1년 이상 보관하고, 위·변조 방지 조치와 정기 점검 주기를 명시한다.'),
('정보시스템 보안성 검토 안내(합성 예시)', '2026 합성판', '5장 1절',
 '["review.encryption","data.personal_info"]', '["SECURITY_REVIEW","FIREWALL"]',
 '개인정보 등 중요정보는 저장·전송 구간 모두 안전한 암호 알고리즘으로 보호한다. 외부(인터넷) 전송 시 전송 구간 암호화는 필수다.');

INSERT INTO private_contexts (kind, name, aliases, attributes, visible_roles, note) VALUES
('SERVER', '합성 업무서버 01', '["WAS-01","민원 WAS"]',
 '{"ip":"10.0.1.10","zone":"내부망(합성)","os":"Linux","owner":"정보화부(합성)"}', '["INFRA","SECURITY"]', '합성 데이터'),
('SERVER', '합성 DB서버 01', '["DB-01"]',
 '{"ip":"10.0.2.20","zone":"DB망(합성)","os":"Linux","owner":"정보화부(합성)"}', '["INFRA","SECURITY"]', '합성 데이터'),
('NETWORK_ZONE', '합성 내부망', '["업무망"]',
 '{"cidr":"10.0.0.0/16","description":"합성 예시 업무망"}', '["INFRA","SECURITY"]', '합성 데이터'),
('NETWORK_ZONE', '합성 DMZ', '["대외망"]',
 '{"cidr":"192.0.2.0/24","description":"합성 예시 — RFC 5737 문서용 대역"}', '["INFRA","SECURITY"]', '합성 데이터');
