package kr.securityadvisor.common.security;

import kr.securityadvisor.common.error.ForbiddenException;

/**
 * 로그인한 사용자의 식별 정보. 세션에 저장되며 서비스 계층의 권한 검사에 쓴다.
 *
 * <p>auth 컨텍스트의 Member 엔티티를 세션에 넣지 않고 이 불변 record 만 둔다. 다른 컨텍스트는 이 타입만 안다.
 *
 * @param username 로그인 아이디(사용자 식별자, members 의 기본 키)
 * @param displayName 화면 표시 이름
 * @param role 역할
 */
public record MemberPrincipal(String username, String displayName, MemberRole role, long sessionVersion) {

    /** 초기 계정의 권한 정보. 로그인은 반드시 DB에서 읽은 sessionVersion을 명시한다. */
    public MemberPrincipal(String username, String displayName, MemberRole role) {
        this(username, displayName, role, 0);
    }

    public boolean hasRole(MemberRole expected) {
        return role == expected;
    }

    /**
     * 요구 역할 중 하나인지 검사한다.
     *
     * @throws ForbiddenException 어느 역할에도 해당하지 않을 때
     */
    public void requireAnyRole(MemberRole... allowed) {
        for (MemberRole candidate : allowed) {
            if (candidate == role) {
                return;
            }
        }
        throw new ForbiddenException("이 작업을 수행할 권한이 없습니다");
    }

    /**
     * 자기 자원인지 검사한다(예: 신청자는 자기 신청만 수정).
     *
     * @throws ForbiddenException 소유자가 다를 때
     */
    public void requireOwner(String ownerUsername) {
        if (!username.equals(ownerUsername)) {
            throw new ForbiddenException("다른 사용자의 자원에는 접근할 수 없습니다");
        }
    }
}
