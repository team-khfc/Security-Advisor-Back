-- 사용자 계정(시연용 인증, F-11). 단일 기관 운영이므로 기관 테이블은 없다(ADR-0003). 시드는 전부 합성 모의데이터다.
-- 이미 main 에 머지된 마이그레이션 파일은 절대 고치지 않는다. 변경은 새 파일로 추가한다.

CREATE TABLE members
(
    username      VARCHAR(50)  PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('APPLICANT', 'SECURITY', 'INFRA', 'ADMIN')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE members IS '시연용 사용자 계정. 로그인 아이디(username)가 기본 키. 역할 4종(APPLICANT/SECURITY/INFRA/ADMIN)';
COMMENT ON COLUMN members.password_hash IS 'Spring Security DelegatingPasswordEncoder 형식({bcrypt}...)';

-- 시연 계정. 비밀번호는 모두 'password1!' (bcrypt). 운영 데이터가 아니다.
INSERT INTO members (username, password_hash, display_name, role)
VALUES ('applicant', '{bcrypt}$2b$10$drydu4fbuEP/mUFx.Tsft.pcKgTSWIZnw98PG7fznS7uAUkTvzaxu', '김신청', 'APPLICANT'),
       ('security', '{bcrypt}$2b$10$drydu4fbuEP/mUFx.Tsft.pcKgTSWIZnw98PG7fznS7uAUkTvzaxu', '이보안', 'SECURITY'),
       ('infra', '{bcrypt}$2b$10$drydu4fbuEP/mUFx.Tsft.pcKgTSWIZnw98PG7fznS7uAUkTvzaxu', '박인프라', 'INFRA'),
       ('admin', '{bcrypt}$2b$10$drydu4fbuEP/mUFx.Tsft.pcKgTSWIZnw98PG7fznS7uAUkTvzaxu', '최관리', 'ADMIN');
