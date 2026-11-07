-- 사용자 계정(시연용 인증, F-11). 운영 기관은 설치당 1곳 고정이라 기관 테이블·기관 키는 없다(FUNCTIONAL_SPEC 3-4장).
-- 시연 계정은 여기서 INSERT 하지 않는다. 비밀번호를 마이그레이션에 평문·해시로 두지 않기 위해
-- 애플리케이션 기동 시 DemoAccountSeeder 가 환경변수(DEMO_ACCOUNT_PASSWORD)로 받아 bcrypt 해시로 저장한다.
-- 이미 main 에 머지된 마이그레이션 파일은 절대 고치지 않는다. 변경은 새 파일로 추가한다.

CREATE TABLE members
(
    username           VARCHAR(50)  PRIMARY KEY,
    password_hash      VARCHAR(100) NOT NULL,
    display_name       VARCHAR(100) NOT NULL,
    department         VARCHAR(100),
    role               VARCHAR(20)  NOT NULL CHECK (role IN ('APPLICANT', 'SECURITY', 'INFRA', 'ADMIN')),
    signup_source      VARCHAR(10)  NOT NULL CHECK (signup_source IN ('SEED', 'SELF')),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_count INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE members IS '사용자 계정. 로그인 아이디(username)가 기본 키. 역할 4종(APPLICANT/SECURITY/INFRA/ADMIN). 운영 기관 1곳 소속이라 기관 키 없음';
COMMENT ON COLUMN members.password_hash IS 'Spring Security DelegatingPasswordEncoder 형식({bcrypt}...). 평문 저장 금지';
COMMENT ON COLUMN members.department IS '부서(선택). 회원가입 시 입력';
COMMENT ON COLUMN members.signup_source IS '가입 경로: SEED(시연 계정 자동 생성) / SELF(회원가입)';
COMMENT ON COLUMN members.failed_login_count IS '연속 로그인 실패 횟수. 성공 시 0으로 초기화';
COMMENT ON COLUMN members.locked_until IS '이 시각까지 로그인 잠금. NULL 이면 잠금 없음';
