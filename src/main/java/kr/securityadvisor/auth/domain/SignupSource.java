package kr.securityadvisor.auth.domain;

/** 계정이 어떻게 만들어졌는지. 시연 계정과 평가자 직접 가입 계정을 구분한다(F-11). */
public enum SignupSource {
    /** 기동 시 환경변수 비밀번호로 자동 생성된 시연 계정 */
    SEED,
    /** 회원가입 API 로 만든 계정 — 항상 APPLICANT */
    SELF
}
