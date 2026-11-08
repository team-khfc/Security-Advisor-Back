-- F-04 담당자 AI 질의: 배정된 신청을 컨텍스트로 담당자가 이어가는 대화. 메시지는 업무 기록으로 저장하되
-- 감사 이력에는 ID 만 남긴다(불변식 25). 외래 키 제약은 두지 않는다(팀 결정).
CREATE TABLE inquiries
(
    inquiry_id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT      NOT NULL,
    staff_username      VARCHAR(50) NOT NULL,
    field               VARCHAR(20) NOT NULL CHECK (field IN ('INFRA', 'SECURITY')),
    title               VARCHAR(200),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inquiries_request_staff ON inquiries (security_request_id, staff_username);
COMMENT ON TABLE inquiries IS '담당자 AI 질의 대화(신청·담당자 단위). 조회는 본인만';

CREATE TABLE inquiry_messages
(
    inquiry_message_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inquiry_id         BIGINT       NOT NULL,
    role               VARCHAR(20)  NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    text               TEXT         NOT NULL,
    evidence_refs      JSONB        NOT NULL,
    model_reference    VARCHAR(200),
    flagged            BOOLEAN      NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inquiry_messages_inquiry ON inquiry_messages (inquiry_id, inquiry_message_id);
COMMENT ON COLUMN inquiry_messages.evidence_refs IS '인용한 지식 조각(S<id>)·기관 사실(P<id>) 태그';
COMMENT ON COLUMN inquiry_messages.flagged IS '근거 없는 내부 정보가 감지되어 가려진 답변';
