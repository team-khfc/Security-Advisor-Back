-- 보안 절차 테이블과 기본 절차 3종.
-- 이미 main 에 머지된 마이그레이션 파일은 절대 고치지 않는다. 변경은 새 파일로 추가한다.

CREATE TABLE procedures
(
    procedure_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE procedures IS '보안 절차 종류 (보안성검토, 방화벽 허용 신청 등)';
COMMENT ON COLUMN procedures.code IS '절차 고정 코드. API·화면에서 id 대신 사용';

INSERT INTO procedures (code, name, description)
VALUES ('SECURITY_REVIEW', '정보화사업 보안성검토', '정보화사업 보안성검토 의뢰서와 자체 점검 결과를 작성합니다.'),
       ('FIREWALL_REQUEST', '방화벽 정책 허용 신청', '방화벽 정책 허용 신청서를 작성합니다.'),
       ('USB_REQUEST', 'USB 사용 신청', '보안 USB 사용 신청서를 작성합니다.');
