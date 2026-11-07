package kr.securityadvisor.auth.dto;

import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 로그인한 사용자 정보. 화면은 role 을 메뉴 표시에만 쓰고, 권한 판단은 서버가 한다.
 *
 * @param username 로그인 아이디(사용자 식별자)
 * @param displayName 표시 이름
 * @param role 역할
 */
public record MemberResponse(String username, String displayName, MemberRole role) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getUsername(), member.getDisplayName(), member.getRole());
    }

    /** 로그인 결과: 세션에 저장할 principal 과 응답 본문을 함께 돌려준다. */
    public record LoginResult(MemberPrincipal principal, MemberResponse member) {}
}
