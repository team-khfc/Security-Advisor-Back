package kr.securityadvisor.common.security;

/** 세션의 권한 정보가 현재 계정과 일치하는지 확인하는 인증 경계. */
public interface AccountSessionValidator {
    /** 삭제·비활성 계정 또는 권한 변경 전 세션이면 false. */
    boolean isValid(MemberPrincipal principal);
}
